// Pure screening logic for Auvex Guard.
//
// This module deliberately contains NO chrome.* / browser.* API calls and no DOM access. It is the
// single place that decides "given a prompt and a gateway, what should happen?" so that the decision
// can be unit-tested in plain Node (see test/screen.test.js) with a mocked fetch and no real network.
//
// The two responsibilities are:
//   1. Talk to the Auvex gateway's /v1/moderations endpoint and parse its envelope into a verdict.
//   2. Turn that verdict (or a failure) into an allow / warn / block decision, honouring the
//      fail-open vs fail-closed policy the user chose in options.

// Decision codes returned to the caller (content script). Kept as plain strings so they survive
// structured-clone across the messaging boundary without any enum machinery.
export const DECISION = Object.freeze({
  ALLOW: 'allow', // nothing found (or screening disabled) — let the prompt through silently
  WARN: 'warn', // findings present, fail-OPEN policy — warn but let the user send anyway
  BLOCK: 'block', // findings present under fail-CLOSED, OR gateway error under fail-CLOSED
});

// Why the gateway could not be consulted. Surfaced to the UI so the popup can show an honest status
// instead of pretending everything is fine.
export const SCREEN_ERROR = Object.freeze({
  NONE: 'none',
  NETWORK: 'network', // fetch threw — gateway unreachable, DNS, CORS, offline, etc.
  HTTP: 'http', // gateway answered with a non-2xx status (e.g. 401 bad key, 429, 5xx)
  BAD_RESPONSE: 'bad_response', // 2xx but the body was not a recognisable moderation envelope
});

/**
 * Normalise the gateway base URL into "<scheme>://host[:port]" with no trailing slash, so we can
 * safely append "/v1/...". Throws on anything that is not an http(s) URL, which lets the options
 * page validate input before saving.
 *
 * @param {string} baseUrl
 * @returns {string} normalised origin-and-path prefix
 */
export function normalizeBaseUrl(baseUrl) {
  if (typeof baseUrl !== 'string' || baseUrl.trim() === '') {
    throw new Error('Gateway base URL is empty.');
  }
  let parsed;
  try {
    parsed = new URL(baseUrl.trim());
  } catch {
    throw new Error('Gateway base URL is not a valid URL.');
  }
  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
    throw new Error('Gateway base URL must use http or https.');
  }
  // Preserve any sub-path the user configured (e.g. behind a reverse proxy at /auvex) but drop a
  // single trailing slash so concatenation stays clean.
  const prefix = parsed.origin + parsed.pathname;
  return prefix.endsWith('/') ? prefix.slice(0, -1) : prefix;
}

/**
 * Parse the JSON body of a /v1/moderations response into a stable verdict shape, tolerating missing
 * or oddly-typed fields rather than trusting the gateway blindly. Returns null if the body has no
 * recognisable "flagged" signal at all (caller treats that as BAD_RESPONSE).
 *
 * Expected envelope: { flagged: boolean, sensitiveData: {type: count}, injection: [categories] }
 *
 * @param {unknown} body parsed JSON
 * @returns {{flagged: boolean, sensitiveData: Object<string, number>, injection: string[]} | null}
 */
export function parseModerationEnvelope(body) {
  if (body === null || typeof body !== 'object') {
    return null;
  }
  // "flagged" is the one field we require. Some gateways may omit it and only send the detail maps;
  // in that case we derive it from the details so we still fail safe.
  const hasFlagged = typeof body.flagged === 'boolean';

  const sensitiveData = {};
  if (body.sensitiveData && typeof body.sensitiveData === 'object') {
    for (const [type, count] of Object.entries(body.sensitiveData)) {
      const n = Number(count);
      if (Number.isFinite(n) && n > 0) {
        sensitiveData[type] = n;
      }
    }
  }

  const injection = Array.isArray(body.injection)
    ? body.injection.filter((c) => typeof c === 'string' && c.length > 0)
    : [];

  const derivedFlag = Object.keys(sensitiveData).length > 0 || injection.length > 0;

  if (!hasFlagged && !derivedFlag) {
    // No "flagged" boolean and no details either. If the body was a genuine "clean" result the
    // gateway would have sent flagged:false, so a body with neither field is unrecognisable.
    return null;
  }

  return {
    flagged: hasFlagged ? body.flagged : derivedFlag,
    sensitiveData,
    injection,
  };
}

/**
 * Call the gateway's moderation endpoint for a single prompt.
 *
 * This is intentionally injectable: the caller passes the `fetch` implementation, which lets tests
 * supply a stub and lets the background worker pass the real global fetch. No retries here — the
 * caller's fail-open/closed policy decides what a failure means, and a hung retry would just delay
 * the user's send.
 *
 * @param {object} args
 * @param {string} args.text the prompt text to screen
 * @param {string} args.baseUrl gateway base URL (will be normalised)
 * @param {string} args.apiKey gateway API key (sent as a Bearer token)
 * @param {typeof fetch} args.fetchImpl fetch implementation to use
 * @param {AbortSignal} [args.signal] optional abort signal (e.g. timeout)
 * @returns {Promise<{
 *   ok: boolean,
 *   error: string,            // one of SCREEN_ERROR
 *   status: number,           // HTTP status, or 0 if the request never completed
 *   message: string,          // human-readable detail for the UI
 *   verdict: ({flagged: boolean, sensitiveData: Object, injection: string[]} | null)
 * }>}
 */
export async function callModeration({ text, baseUrl, apiKey, fetchImpl, signal }) {
  let url;
  try {
    url = normalizeBaseUrl(baseUrl) + '/v1/moderations';
  } catch (e) {
    return {
      ok: false,
      error: SCREEN_ERROR.NETWORK,
      status: 0,
      message: e.message,
      verdict: null,
    };
  }

  let response;
  try {
    response = await fetchImpl(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer ' + (apiKey || ''),
      },
      body: JSON.stringify({ input: text }),
      signal,
    });
  } catch (e) {
    // Thrown fetch == unreachable gateway, offline, aborted/timed-out, CORS rejection, etc.
    return {
      ok: false,
      error: SCREEN_ERROR.NETWORK,
      status: 0,
      message: 'Could not reach the Auvex gateway: ' + (e && e.message ? e.message : 'network error'),
      verdict: null,
    };
  }

  if (!response.ok) {
    return {
      ok: false,
      error: SCREEN_ERROR.HTTP,
      status: response.status,
      message: 'Auvex gateway returned HTTP ' + response.status + '.',
      verdict: null,
    };
  }

  let body;
  try {
    body = await response.json();
  } catch {
    return {
      ok: false,
      error: SCREEN_ERROR.BAD_RESPONSE,
      status: response.status,
      message: 'Auvex gateway response was not valid JSON.',
      verdict: null,
    };
  }

  const verdict = parseModerationEnvelope(body);
  if (verdict === null) {
    return {
      ok: false,
      error: SCREEN_ERROR.BAD_RESPONSE,
      status: response.status,
      message: 'Auvex gateway response was not a recognisable moderation result.',
      verdict: null,
    };
  }

  return {
    ok: true,
    error: SCREEN_ERROR.NONE,
    status: response.status,
    message: 'ok',
    verdict,
  };
}

/**
 * Turn a moderation result (the output of callModeration) into the final allow/warn/block decision,
 * applying the fail-open vs fail-closed policy.
 *
 *   - Successful clean result            -> ALLOW
 *   - Successful flagged result          -> BLOCK if failClosed else WARN
 *   - Gateway error (network/http/parse) -> BLOCK if failClosed else ALLOW (fail OPEN = don't block)
 *
 * Note the asymmetry that fail-open requires: when the gateway is down we ALLOW (never warn), because
 * a warning the user can't act on just trains them to click through. When findings exist we WARN, so
 * the user makes an informed choice. fail-closed collapses both "can't tell" and "found something"
 * into BLOCK, which is the strict posture for regulated environments.
 *
 * @param {{ok: boolean, error: string, verdict: (object|null)}} result from callModeration
 * @param {boolean} failClosed user's policy: true = block on doubt, false = fail open
 * @returns {{decision: string, flagged: boolean, reason: string}}
 */
export function decideFromResult(result, failClosed) {
  if (result.ok && result.verdict) {
    if (!result.verdict.flagged) {
      return { decision: DECISION.ALLOW, flagged: false, reason: 'clean' };
    }
    return {
      decision: failClosed ? DECISION.BLOCK : DECISION.WARN,
      flagged: true,
      reason: 'findings',
    };
  }

  // Gateway could not give us a usable verdict.
  if (failClosed) {
    return { decision: DECISION.BLOCK, flagged: false, reason: 'gateway-error-fail-closed' };
  }
  return { decision: DECISION.ALLOW, flagged: false, reason: 'gateway-error-fail-open' };
}

/**
 * Build a short, human-readable summary of what was found, for the in-page banner and the log.
 * Pure string formatting — kept here so it is covered by the same tests.
 *
 * @param {{sensitiveData: Object<string, number>, injection: string[]}} verdict
 * @returns {string[]} bullet-ready lines, empty if nothing was found
 */
export function summarizeFindings(verdict) {
  if (!verdict) return [];
  const lines = [];
  const sensitive = verdict.sensitiveData || {};
  for (const [type, count] of Object.entries(sensitive)) {
    const label = type.replace(/_/g, ' ');
    lines.push(count > 1 ? `${count}× ${label}` : label);
  }
  for (const category of verdict.injection || []) {
    lines.push('prompt injection: ' + category.replace(/_/g, ' '));
  }
  return lines;
}

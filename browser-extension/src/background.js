// Auvex Guard background service worker (MV3, type: module).
//
// Responsibilities:
//   - Hold the only code path that performs network I/O to the user's Auvex gateway. Content scripts
//     run in the page origin and must NOT call the gateway directly (the page could read the key and
//     CORS would fight us); they message us instead.
//   - Apply the user's settings (gateway URL, key, fail-open/closed, per-site enable) to each screen.
//   - Keep a rolling log of findings, capped, in chrome.storage so the popup can show recent activity
//     and so a worker restart (MV3 workers are ephemeral) doesn't lose it.
//
// The actual screening + decision logic lives in the pure, testable src/screen.js module.

import {
  callModeration,
  decideFromResult,
  summarizeFindings,
  normalizeBaseUrl,
  DECISION,
  SCREEN_ERROR,
} from './screen.js';

// ---- Settings -------------------------------------------------------------------------------------

const DEFAULTS = Object.freeze({
  baseUrl: 'http://localhost:8080',
  apiKey: '',
  failClosed: false, // default fail OPEN: never block the user just because the gateway is down
  sites: {
    // Per-site enable flags. Keyed by the adapter id used in content.js / SITE_ADAPTERS.
    chatgpt: true,
    claude: true,
    gemini: true,
  },
});

const LOG_KEY = 'findingsLog';
const LOG_CAP = 50; // keep at most this many entries; oldest dropped first
const REQUEST_TIMEOUT_MS = 6000; // bound each moderation call so a hung gateway can't freeze a send

/** Read settings, merged over defaults so a partial/empty store still yields a complete object. */
async function getSettings() {
  const stored = await chrome.storage.local.get(['baseUrl', 'apiKey', 'failClosed', 'sites']);
  return {
    baseUrl: stored.baseUrl ?? DEFAULTS.baseUrl,
    apiKey: stored.apiKey ?? DEFAULTS.apiKey,
    failClosed: stored.failClosed ?? DEFAULTS.failClosed,
    sites: { ...DEFAULTS.sites, ...(stored.sites || {}) },
  };
}

// Seed defaults on install so the options page and popup have something coherent to show.
chrome.runtime.onInstalled.addListener(async () => {
  const current = await chrome.storage.local.get(Object.keys(DEFAULTS));
  const seed = {};
  for (const [k, v] of Object.entries(DEFAULTS)) {
    if (current[k] === undefined) seed[k] = v;
  }
  if (Object.keys(seed).length > 0) {
    await chrome.storage.local.set(seed);
  }
});

// ---- Findings log ---------------------------------------------------------------------------------

async function readLog() {
  const { [LOG_KEY]: log } = await chrome.storage.local.get(LOG_KEY);
  return Array.isArray(log) ? log : [];
}

/** Prepend an entry, cap the list, and persist. Returns the new length for convenience. */
async function appendLog(entry) {
  const log = await readLog();
  log.unshift(entry);
  if (log.length > LOG_CAP) log.length = LOG_CAP;
  await chrome.storage.local.set({ [LOG_KEY]: log });
  return log.length;
}

// ---- Timeout helper -------------------------------------------------------------------------------

// AbortSignal.timeout exists in modern workers, but we build it manually for the widest compatibility
// and so we can clear the timer promptly on success.
function withTimeout(ms) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), ms);
  return { signal: controller.signal, cancel: () => clearTimeout(timer) };
}

// ---- Core: screen one prompt ----------------------------------------------------------------------

/**
 * Screen a prompt for a given site and return the verdict + decision the content script should act on.
 * Also records flagged outcomes (and gateway errors) to the log.
 *
 * @param {string} text prompt text
 * @param {string} siteId adapter id (chatgpt | claude | gemini)
 * @param {string} pageUrl the page the prompt came from (for the log; not the gateway URL)
 */
async function screenPrompt(text, siteId, pageUrl) {
  const settings = await getSettings();

  // Respect the per-site toggle: if this site is disabled, allow without contacting the gateway.
  if (settings.sites[siteId] === false) {
    return {
      decision: DECISION.ALLOW,
      flagged: false,
      disabled: true,
      findings: [],
      status: { ok: true, error: SCREEN_ERROR.NONE, message: 'Screening disabled for this site.' },
    };
  }

  if (!text || text.trim() === '') {
    return {
      decision: DECISION.ALLOW,
      flagged: false,
      findings: [],
      status: { ok: true, error: SCREEN_ERROR.NONE, message: 'Empty prompt.' },
    };
  }

  const { signal, cancel } = withTimeout(REQUEST_TIMEOUT_MS);
  let result;
  try {
    result = await callModeration({
      text,
      baseUrl: settings.baseUrl,
      apiKey: settings.apiKey,
      fetchImpl: fetch,
      signal,
    });
  } finally {
    cancel();
  }

  const decision = decideFromResult(result, settings.failClosed);
  const findings = result.verdict ? summarizeFindings(result.verdict) : [];

  // Log anything noteworthy: real findings, or gateway errors (so the user can see the guard was
  // blind). Clean allows are not logged to keep the list signal-rich and the cap meaningful.
  if (decision.flagged || !result.ok) {
    await appendLog({
      time: Date.now(),
      site: siteId,
      url: pageUrl || '',
      decision: decision.decision,
      flagged: decision.flagged,
      findings,
      // For gateway errors, capture why so the popup can explain a fail-open/closed event.
      error: result.ok ? SCREEN_ERROR.NONE : result.error,
      message: result.message,
    });
  }

  return {
    decision: decision.decision,
    flagged: decision.flagged,
    reason: decision.reason,
    findings,
    status: {
      ok: result.ok,
      error: result.error,
      status: result.status,
      message: result.message,
    },
  };
}

// ---- Connection test (for popup + options) --------------------------------------------------------

/**
 * Probe the gateway by GETting /v1/models with the configured key. Distinguishes "unreachable" from
 * "reachable but unauthorised" (401) so the user can tell a wrong-URL from a wrong-key.
 */
async function testConnection(overrideBaseUrl, overrideApiKey) {
  const settings = await getSettings();
  const baseUrl = overrideBaseUrl ?? settings.baseUrl;
  const apiKey = overrideApiKey ?? settings.apiKey;

  let url;
  try {
    url = normalizeBaseUrl(baseUrl) + '/v1/models';
  } catch (e) {
    return { ok: false, reachable: false, status: 0, message: e.message };
  }

  const { signal, cancel } = withTimeout(REQUEST_TIMEOUT_MS);
  try {
    const response = await fetch(url, {
      method: 'GET',
      headers: { Authorization: 'Bearer ' + (apiKey || '') },
      signal,
    });
    if (response.ok) {
      return { ok: true, reachable: true, status: response.status, message: 'Gateway reachable.' };
    }
    if (response.status === 401) {
      return {
        ok: false,
        reachable: true,
        status: 401,
        message: 'Reachable, but the API key was rejected (401).',
      };
    }
    return {
      ok: false,
      reachable: true,
      status: response.status,
      message: 'Gateway returned HTTP ' + response.status + '.',
    };
  } catch (e) {
    return {
      ok: false,
      reachable: false,
      status: 0,
      message: 'Could not reach the gateway: ' + (e && e.message ? e.message : 'network error'),
    };
  } finally {
    cancel();
  }
}

// ---- Message router -------------------------------------------------------------------------------

// Single onMessage listener. Each handler returns a promise; we keep the channel open (return true)
// and call sendResponse when it resolves. Unknown messages get an explicit error so callers don't
// hang waiting.
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  const type = message && message.type;

  if (type === 'SCREEN_PROMPT') {
    const pageUrl = (sender && sender.tab && sender.tab.url) || message.url || '';
    screenPrompt(message.text, message.siteId, pageUrl)
      .then(sendResponse)
      .catch((e) =>
        // Never let an unexpected error escape as a block: report it as a gateway error so the
        // content script applies the user's fail-open/closed policy consistently.
        sendResponse({
          decision: DECISION.ALLOW,
          flagged: false,
          findings: [],
          status: { ok: false, error: SCREEN_ERROR.NETWORK, status: 0, message: String(e) },
        }),
      );
    return true;
  }

  if (type === 'GET_STATUS') {
    Promise.all([testConnection(), readLog(), getSettings()])
      .then(([connection, log, settings]) =>
        sendResponse({
          connection,
          recentCount: log.length,
          recent: log.slice(0, 5),
          settings: {
            baseUrl: settings.baseUrl,
            failClosed: settings.failClosed,
            hasApiKey: settings.apiKey !== '',
            sites: settings.sites,
          },
        }),
      )
      .catch((e) => sendResponse({ error: String(e) }));
    return true;
  }

  if (type === 'TEST_CONNECTION') {
    testConnection(message.baseUrl, message.apiKey)
      .then(sendResponse)
      .catch((e) => sendResponse({ ok: false, reachable: false, status: 0, message: String(e) }));
    return true;
  }

  if (type === 'CLEAR_LOG') {
    chrome.storage.local
      .set({ [LOG_KEY]: [] })
      .then(() => sendResponse({ ok: true }))
      .catch((e) => sendResponse({ ok: false, message: String(e) }));
    return true;
  }

  // Unrecognised message: respond so the sender's promise settles instead of timing out.
  sendResponse({ error: 'Unknown message type: ' + String(type) });
  return false;
});

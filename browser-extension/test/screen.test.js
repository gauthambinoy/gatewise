// Unit tests for the pure screening core (src/screen.js).
// Run with:  node --test
//
// No real network is ever touched: every test passes a stub `fetchImpl`. These tests pin the three
// behaviours that matter most: the flagged->block / clean->allow verdict path, the fail-open vs
// fail-closed decision on a gateway error, and the envelope parsing that protects us from a
// malformed gateway response.

import { test } from 'node:test';
import assert from 'node:assert/strict';

import {
  callModeration,
  decideFromResult,
  parseModerationEnvelope,
  normalizeBaseUrl,
  summarizeFindings,
  DECISION,
  SCREEN_ERROR,
} from '../src/screen.js';

// ---- Fetch stubs ----------------------------------------------------------------------------------

/** A fetch that returns a JSON body with the given status. */
function jsonFetch(body, status = 200) {
  return async () => ({
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  });
}

/** A fetch that rejects, simulating an unreachable gateway / offline. */
function throwingFetch(message = 'network down') {
  return async () => {
    throw new Error(message);
  };
}

/** A fetch returning a 2xx but with a body that fails JSON parsing. */
function badJsonFetch(status = 200) {
  return async () => ({
    ok: status >= 200 && status < 300,
    status,
    json: async () => {
      throw new Error('Unexpected token');
    },
  });
}

const ARGS = { baseUrl: 'http://localhost:8080', apiKey: 'test-key' };

// ---- normalizeBaseUrl -----------------------------------------------------------------------------

test('normalizeBaseUrl strips a trailing slash and keeps the origin', () => {
  assert.equal(normalizeBaseUrl('http://localhost:8080/'), 'http://localhost:8080');
  assert.equal(normalizeBaseUrl('http://localhost:8080'), 'http://localhost:8080');
  assert.equal(normalizeBaseUrl('https://gw.example.com/auvex/'), 'https://gw.example.com/auvex');
});

test('normalizeBaseUrl rejects empty and non-http(s) URLs', () => {
  assert.throws(() => normalizeBaseUrl(''));
  assert.throws(() => normalizeBaseUrl('   '));
  assert.throws(() => normalizeBaseUrl('not a url'));
  assert.throws(() => normalizeBaseUrl('ftp://example.com'));
});

// ---- parseModerationEnvelope ----------------------------------------------------------------------

test('parseModerationEnvelope reads a clean result', () => {
  const v = parseModerationEnvelope({ flagged: false, sensitiveData: {}, injection: [] });
  assert.deepEqual(v, { flagged: false, sensitiveData: {}, injection: [] });
});

test('parseModerationEnvelope reads sensitive data and injection findings', () => {
  const v = parseModerationEnvelope({
    flagged: true,
    sensitiveData: { email: 1, api_key: 2 },
    injection: ['instruction_override'],
  });
  assert.equal(v.flagged, true);
  assert.deepEqual(v.sensitiveData, { email: 1, api_key: 2 });
  assert.deepEqual(v.injection, ['instruction_override']);
});

test('parseModerationEnvelope derives flagged when details exist but the boolean is missing', () => {
  const v = parseModerationEnvelope({ sensitiveData: { email: 1 } });
  assert.equal(v.flagged, true);
});

test('parseModerationEnvelope ignores junk counts and non-string injection categories', () => {
  const v = parseModerationEnvelope({
    flagged: true,
    sensitiveData: { email: 'x', ssn: 0, phone: 3 },
    injection: ['ok', 42, '', null],
  });
  assert.deepEqual(v.sensitiveData, { phone: 3 }); // 'x' and 0 dropped
  assert.deepEqual(v.injection, ['ok']);
});

test('parseModerationEnvelope returns null for an unrecognisable body', () => {
  assert.equal(parseModerationEnvelope(null), null);
  assert.equal(parseModerationEnvelope('nope'), null);
  assert.equal(parseModerationEnvelope({}), null); // no flagged, no details
  assert.equal(parseModerationEnvelope({ something: 'else' }), null);
});

// ---- callModeration: success paths ----------------------------------------------------------------

test('callModeration returns a clean verdict on a flagged:false response', async () => {
  const res = await callModeration({
    ...ARGS,
    text: 'hello',
    fetchImpl: jsonFetch({ flagged: false, sensitiveData: {}, injection: [] }),
  });
  assert.equal(res.ok, true);
  assert.equal(res.error, SCREEN_ERROR.NONE);
  assert.equal(res.verdict.flagged, false);
});

test('callModeration returns a flagged verdict on findings', async () => {
  const res = await callModeration({
    ...ARGS,
    text: 'my email is a@b.com',
    fetchImpl: jsonFetch({ flagged: true, sensitiveData: { email: 1 }, injection: [] }),
  });
  assert.equal(res.ok, true);
  assert.equal(res.verdict.flagged, true);
  assert.deepEqual(res.verdict.sensitiveData, { email: 1 });
});

test('callModeration sends the prompt as {input} with a Bearer header', async () => {
  let capturedUrl;
  let capturedInit;
  const spyFetch = async (url, init) => {
    capturedUrl = url;
    capturedInit = init;
    return { ok: true, status: 200, json: async () => ({ flagged: false, sensitiveData: {}, injection: [] }) };
  };
  await callModeration({ ...ARGS, text: 'screen me', fetchImpl: spyFetch });
  assert.equal(capturedUrl, 'http://localhost:8080/v1/moderations');
  assert.equal(capturedInit.method, 'POST');
  assert.equal(capturedInit.headers.Authorization, 'Bearer test-key');
  assert.deepEqual(JSON.parse(capturedInit.body), { input: 'screen me' });
});

// ---- callModeration: error paths ------------------------------------------------------------------

test('callModeration reports NETWORK when fetch throws', async () => {
  const res = await callModeration({ ...ARGS, text: 'x', fetchImpl: throwingFetch() });
  assert.equal(res.ok, false);
  assert.equal(res.error, SCREEN_ERROR.NETWORK);
  assert.equal(res.verdict, null);
});

test('callModeration reports HTTP on a non-2xx status', async () => {
  const res = await callModeration({ ...ARGS, text: 'x', fetchImpl: jsonFetch({ error: 'bad key' }, 401) });
  assert.equal(res.ok, false);
  assert.equal(res.error, SCREEN_ERROR.HTTP);
  assert.equal(res.status, 401);
});

test('callModeration reports BAD_RESPONSE when JSON parsing fails', async () => {
  const res = await callModeration({ ...ARGS, text: 'x', fetchImpl: badJsonFetch() });
  assert.equal(res.ok, false);
  assert.equal(res.error, SCREEN_ERROR.BAD_RESPONSE);
});

test('callModeration reports BAD_RESPONSE when the envelope is unrecognisable', async () => {
  const res = await callModeration({ ...ARGS, text: 'x', fetchImpl: jsonFetch({ totally: 'wrong' }) });
  assert.equal(res.ok, false);
  assert.equal(res.error, SCREEN_ERROR.BAD_RESPONSE);
});

// ---- decideFromResult: the policy matrix ----------------------------------------------------------

test('clean verdict -> ALLOW regardless of policy', () => {
  const ok = { ok: true, error: SCREEN_ERROR.NONE, verdict: { flagged: false, sensitiveData: {}, injection: [] } };
  assert.equal(decideFromResult(ok, false).decision, DECISION.ALLOW);
  assert.equal(decideFromResult(ok, true).decision, DECISION.ALLOW);
});

test('flagged verdict -> WARN under fail-open, BLOCK under fail-closed', () => {
  const flagged = {
    ok: true,
    error: SCREEN_ERROR.NONE,
    verdict: { flagged: true, sensitiveData: { email: 1 }, injection: [] },
  };
  assert.equal(decideFromResult(flagged, false).decision, DECISION.WARN);
  assert.equal(decideFromResult(flagged, true).decision, DECISION.BLOCK);
  assert.equal(decideFromResult(flagged, false).flagged, true);
});

test('gateway error -> ALLOW under fail-open (do not block on outage)', () => {
  const err = { ok: false, error: SCREEN_ERROR.NETWORK, verdict: null };
  const d = decideFromResult(err, false);
  assert.equal(d.decision, DECISION.ALLOW);
  assert.equal(d.reason, 'gateway-error-fail-open');
});

test('gateway error -> BLOCK under fail-closed (block on doubt)', () => {
  const err = { ok: false, error: SCREEN_ERROR.HTTP, verdict: null };
  const d = decideFromResult(err, true);
  assert.equal(d.decision, DECISION.BLOCK);
  assert.equal(d.reason, 'gateway-error-fail-closed');
});

// ---- End-to-end: call + decide --------------------------------------------------------------------

test('end-to-end: findings under fail-open warn, under fail-closed block', async () => {
  const fetchImpl = jsonFetch({ flagged: true, sensitiveData: { ssn: 1 }, injection: ['jailbreak_persona'] });
  const res = await callModeration({ ...ARGS, text: 'secret', fetchImpl });

  assert.equal(decideFromResult(res, false).decision, DECISION.WARN);
  assert.equal(decideFromResult(res, true).decision, DECISION.BLOCK);
});

test('end-to-end: outage under fail-open allows, under fail-closed blocks', async () => {
  const res = await callModeration({ ...ARGS, text: 'secret', fetchImpl: throwingFetch() });
  assert.equal(decideFromResult(res, false).decision, DECISION.ALLOW);
  assert.equal(decideFromResult(res, true).decision, DECISION.BLOCK);
});

// ---- summarizeFindings ----------------------------------------------------------------------------

test('summarizeFindings produces readable lines with pluralisation', () => {
  const lines = summarizeFindings({
    sensitiveData: { email: 1, api_key: 3 },
    injection: ['instruction_override'],
  });
  assert.ok(lines.includes('email'));
  assert.ok(lines.includes('3× api key'));
  assert.ok(lines.includes('prompt injection: instruction override'));
});

test('summarizeFindings is empty for a clean or null verdict', () => {
  assert.deepEqual(summarizeFindings({ sensitiveData: {}, injection: [] }), []);
  assert.deepEqual(summarizeFindings(null), []);
});

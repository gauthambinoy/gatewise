/**
 * contract.test.mjs — OpenAPI <-> SDK drift guard (no network required).
 *
 * Run with:  node --test contract.test.mjs
 *
 * Why: the JS SDK (sdk/js) calls the gateway by hard-coded `/v1/...` paths. If someone renames
 * or removes an endpoint in the OpenAPI spec (docs/openapi.yaml) without updating the SDK — or
 * vice-versa — clients break silently. This test parses the REAL request paths out of the JS
 * SDK source and asserts each one is a declared path + method in the OpenAPI document.
 *
 * It is intentionally a "real" check: it fails if any SDK-called path is missing/renamed in the
 * spec, and it fails if the SDK stops covering an endpoint we expect it to cover.
 *
 * No HTTP is made — it only reads two files on disk.
 */

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import yaml from 'js-yaml';

const here = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = resolve(here, '..');
const OPENAPI_PATH = resolve(REPO_ROOT, 'docs/openapi.yaml');
const SDK_CLIENT_PATH = resolve(REPO_ROOT, 'sdk/js/src/client.ts');

/**
 * Parse the JS SDK client source for the gateway paths it calls.
 *
 * The SDK joins every path with `/v1/` (see `GateWiseClient.url()`), so a call like
 *   this.request('GET', 'usage')      -> GET  /v1/usage
 *   this.client.request<T>('POST', 'moderations', ...) -> POST /v1/moderations
 *   this.client.stream('chat/completions', ...)        -> POST /v1/chat/completions  (SSE is POST)
 *
 * Returns a de-duplicated array of { method, path } using full `/v1/...` paths.
 */
function parseSdkCalls(source) {
  const calls = new Map(); // key `${method} ${path}` -> { method, path }

  const add = (method, sdkPath) => {
    const clean = sdkPath.replace(/^\/+/, ''); // SDK paths are relative, no leading slash
    const full = `/v1/${clean}`;
    const key = `${method} ${full}`;
    if (!calls.has(key)) calls.set(key, { method, path: full });
  };

  // .request('METHOD', 'path'   — optional generic like .request<ModerationResult>(...)
  const reqRe = /\.request\s*(?:<[^>]*>)?\s*\(\s*['"]([A-Z]+)['"]\s*,\s*['"]([^'"]+)['"]/g;
  for (let m; (m = reqRe.exec(source)); ) add(m[1].toUpperCase(), m[2]);

  // .stream('path'  — streaming chat completions; these are POST requests.
  const streamRe = /\.stream\s*\(\s*['"]([^'"]+)['"]/g;
  for (let m; (m = streamRe.exec(source)); ) add('POST', m[1]);

  return [...calls.values()];
}

const openapiDoc = yaml.load(readFileSync(OPENAPI_PATH, 'utf8'));
const sdkSource = readFileSync(SDK_CLIENT_PATH, 'utf8');
const sdkCalls = parseSdkCalls(sdkSource);

// Endpoints the SDK is REQUIRED to keep covering. If the SDK is gutted/renamed so it no longer
// calls one of these, that is itself a contract break we want to catch.
const REQUIRED_PATHS = [
  '/v1/chat/completions',
  '/v1/embeddings',
  '/v1/moderations',
  '/v1/usage',
  '/v1/audit',
  '/v1/models',
  '/v1/policies',
];

test('OpenAPI doc and JS SDK source both parsed', () => {
  assert.ok(openapiDoc && typeof openapiDoc === 'object', 'openapi.yaml failed to parse');
  assert.ok(openapiDoc.paths && typeof openapiDoc.paths === 'object', 'openapi.yaml has no paths');
  assert.ok(sdkCalls.length > 0, 'no request paths were parsed out of the JS SDK source');
  // Sanity: we expect to find at least the 8 distinct call sites the SDK currently has.
  assert.ok(
    sdkCalls.length >= 8,
    `expected >= 8 SDK call sites, found ${sdkCalls.length}: ${JSON.stringify(sdkCalls)}`,
  );
});

test('every path the SDK calls exists in the OpenAPI spec with the right method', () => {
  const specPaths = openapiDoc.paths;
  for (const { method, path } of sdkCalls) {
    const entry = specPaths[path];
    assert.ok(
      entry,
      `SDK calls ${method} ${path} but the OpenAPI spec declares no such path (renamed/removed?)`,
    );
    const verb = method.toLowerCase();
    assert.ok(
      Object.prototype.hasOwnProperty.call(entry, verb),
      `SDK calls ${method} ${path} but the spec declares no ${method} for it ` +
        `(declared: ${Object.keys(entry).join(', ').toUpperCase()})`,
    );
  }
});

test('SDK still covers every required governance/AI endpoint', () => {
  const calledPaths = new Set(sdkCalls.map((c) => c.path));
  for (const required of REQUIRED_PATHS) {
    assert.ok(
      calledPaths.has(required),
      `the JS SDK no longer calls ${required} — coverage regression`,
    );
  }
});

test('report: number of SDK call paths checked against the spec', () => {
  // Visible, deterministic record of what was verified.
  const lines = sdkCalls
    .map((c) => `  ${c.method.padEnd(4)} ${c.path}`)
    .sort()
    .join('\n');
  console.log(`Checked ${sdkCalls.length} SDK call paths against ${OPENAPI_PATH}:\n${lines}`);
  assert.equal(sdkCalls.length >= REQUIRED_PATHS.length, true);
});

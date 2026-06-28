/*
 * load.k6.js — real k6 load test for the Auvex gateway.
 *
 * For users who have k6 installed (https://k6.io). If you do NOT have k6, use the pure-Node
 * `load.mjs` harness instead (only needs `npm install` + Node 18+).
 *
 * It runs three staged scenarios back-to-back against a gateway:
 *   1. smoke    — 1 VU for a short time, just proves the endpoints answer.
 *   2. load     — a gentle ramp up to a steady baseline and back down (sustained load).
 *   3. spike    — a sharp jump in VUs then a quick drop (burst / resilience).
 *
 * Each iteration exercises:
 *   - GET  /auth/config            (public; no API key needed)
 *   - POST /v1/chat/completions    (only if API_KEY is provided — it is provider-billed and
 *                                   needs auth; without a key we skip it so we don't generate
 *                                   401s that pollute the error-rate threshold)
 *
 * Config via env vars:
 *   BASE_URL   gateway base URL (default https://auvex.54.170.218.176.nip.io)
 *   API_KEY    Auvex API key — enables the chat-completions leg when set
 *
 * Run examples:
 *   k6 run -e BASE_URL=http://localhost:8080 -e API_KEY=auvex_sk_... perf/load.k6.js
 *   k6 run perf/load.k6.js                       # smoke+load+spike against the default URL
 *
 * IMPORTANT: the default BASE_URL points at the small live DEMO box, which rate-limits
 * (~120 req/min). The VU/RPS levels below are deliberately modest. For a REAL capacity test,
 * aim BASE_URL at a dedicated/staging instance and raise the stage targets.
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || 'https://auvex.54.170.218.176.nip.io').replace(/\/+$/, '');
const API_KEY = __ENV.API_KEY || '';

// Custom metrics so we can see each endpoint independently.
const authConfigErrors = new Rate('auth_config_errors');
const chatErrors = new Rate('chat_errors');
const chatLatency = new Trend('chat_latency', true);

export const options = {
  // Run the three scenarios in sequence using startTime offsets.
  scenarios: {
    smoke: {
      executor: 'constant-vus',
      vus: 1,
      duration: '20s',
      tags: { stage: 'smoke' },
      exec: 'flow',
    },
    load: {
      executor: 'ramping-vus',
      startTime: '20s',
      startVUs: 0,
      stages: [
        { duration: '20s', target: 5 }, // ramp up to baseline
        { duration: '40s', target: 5 }, // hold the baseline
        { duration: '10s', target: 0 }, // ramp down
      ],
      tags: { stage: 'load' },
      exec: 'flow',
    },
    spike: {
      executor: 'ramping-vus',
      startTime: '110s',
      startVUs: 0,
      stages: [
        { duration: '5s', target: 20 }, // sharp burst
        { duration: '10s', target: 20 }, // hold the spike
        { duration: '5s', target: 0 }, // recover
      ],
      tags: { stage: 'spike' },
      exec: 'flow',
    },
  },

  // Pass/fail gates. k6 exits non-zero if any threshold is breached.
  thresholds: {
    // Overall request failure rate must stay low.
    http_req_failed: ['rate<0.05'],
    // Overall p95 latency budget.
    http_req_duration: ['p(95)<2000'],
    // Per-endpoint budgets.
    'http_req_duration{endpoint:auth_config}': ['p(95)<800'],
    auth_config_errors: ['rate<0.02'],
    // Chat is only exercised when API_KEY is set; the threshold is generous because it can
    // make a real upstream provider call.
    chat_errors: ['rate<0.10'],
  },
};

export function flow() {
  // --- Public endpoint: GET /auth/config (no auth) ---
  const cfg = http.get(`${BASE_URL}/auth/config`, {
    headers: { Accept: 'application/json' },
    tags: { endpoint: 'auth_config' },
  });
  const cfgOk = check(cfg, {
    'auth/config status is 200': (r) => r.status === 200,
  });
  authConfigErrors.add(!cfgOk);

  // --- Authenticated, provider-billed: POST /v1/chat/completions (only with an API key) ---
  if (API_KEY) {
    const body = JSON.stringify({
      model: 'smart',
      messages: [{ role: 'user', content: 'ping' }],
      stream: false,
    });
    const res = http.post(`${BASE_URL}/v1/chat/completions`, body, {
      headers: {
        Authorization: `Bearer ${API_KEY}`,
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
      tags: { endpoint: 'chat_completions' },
    });
    const ok = check(res, {
      'chat status is 2xx': (r) => r.status >= 200 && r.status < 300,
    });
    chatErrors.add(!ok);
    chatLatency.add(res.timings.duration);
  }

  // Small think-time so VUs don't hammer in a tight loop.
  sleep(1);
}

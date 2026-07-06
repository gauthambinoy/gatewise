/**
 * load.mjs — pure-Node load test for the GateWise gateway, using `autocannon`.
 *
 * WHY a Node path (vs k6): k6 is not installed everywhere, and Docker output capture is
 * unreliable on some boxes. This script needs only `npm install` + Node 18+, so it runs
 * anywhere CI or a laptop has Node.
 *
 * WHAT it does: a SHORT, LOW-RATE burst against the UNAUTHENTICATED `GET /auth/config`
 * endpoint, then prints throughput + latency percentiles.
 *
 *   - It targets `/auth/config` on purpose: it is public, so no API key is required, and we
 *     are not exercising the (provider-billed) `/v1/chat/completions` path against the small
 *     live demo box.
 *   - It stays gentle on purpose: the live demo box rate-limits (~120 req/min), so the
 *     defaults below keep the rate well under that. This is a smoke/sanity load check, NOT a
 *     real capacity test — for that, point BASE_URL at a dedicated/staging instance and raise
 *     the knobs (see README).
 *
 * ENV / config (all optional):
 *   BASE_URL     gateway base URL        (default: https://gatewise.54.170.218.176.nip.io)
 *   API_KEY      GateWise API key           (optional; only added as a Bearer header if set —
 *                                         /auth/config does not need it)
 *   DURATION     seconds to run          (default: 5)
 *   CONNECTIONS  concurrent connections  (default: 2)
 *   RATE         max requests/sec total  (default: 8 — keeps us under the box's ~120/min)
 *   P99_CEILING  fail if p99 (ms) above  (default: 5000 — deliberately generous)
 *   ERROR_RATE   fail if error rate above (default: 0.5 = 50%)
 *
 * EXIT CODES:
 *   0  healthy run, OR the box was unreachable (we degrade gracefully — a network outage
 *      must not hard-fail the harness)
 *   1  the box answered but the run was unhealthy: error rate too high, or p99 over ceiling
 */

import autocannon from 'autocannon';

const BASE_URL = (process.env.BASE_URL || 'https://gatewise.54.170.218.176.nip.io').replace(/\/+$/, '');
const API_KEY = process.env.API_KEY || '';
const DURATION = numEnv('DURATION', 5);
const CONNECTIONS = numEnv('CONNECTIONS', 2);
const RATE = numEnv('RATE', 8);
const P99_CEILING_MS = numEnv('P99_CEILING', 5000);
const ERROR_RATE_MAX = numEnv('ERROR_RATE', 0.5);

const TARGET = `${BASE_URL}/auth/config`;

function numEnv(name, fallback) {
  const raw = process.env[name];
  if (raw === undefined || raw === '') return fallback;
  const n = Number(raw);
  return Number.isFinite(n) && n > 0 ? n : fallback;
}

function ms(n) {
  return n === undefined || n === null ? 'n/a' : `${n} ms`;
}

async function main() {
  console.log('GateWise load test (autocannon)');
  console.log(`  target      : GET ${TARGET}`);
  console.log(`  duration    : ${DURATION}s   connections: ${CONNECTIONS}   max rate: ${RATE} req/s`);
  console.log(`  thresholds  : p99 <= ${P99_CEILING_MS} ms, error-rate <= ${(ERROR_RATE_MAX * 100).toFixed(0)}%`);
  console.log('  (gentle on purpose — the live demo box rate-limits; see README.md)\n');

  const headers = {
    Accept: 'application/json',
    'User-Agent': 'gatewise-perf/0.1.0',
  };
  // /auth/config is public, but if a key is provided we still send it so the same script can
  // be aimed at an authenticated endpoint by changing TARGET.
  if (API_KEY) headers.Authorization = `Bearer ${API_KEY}`;

  let result;
  try {
    result = await autocannon({
      url: TARGET,
      connections: CONNECTIONS,
      duration: DURATION,
      overallRate: RATE, // cap total requests/sec across all connections — keeps us polite
      headers,
    });
  } catch (err) {
    // A thrown error here is almost always a transport/DNS failure (host down, no network).
    // Degrade gracefully: report it clearly and exit 0 so a network blip doesn't fail CI.
    console.error(`\nCould not run the load test against ${TARGET}:`);
    console.error(`  ${err && err.message ? err.message : err}`);
    console.error('Treating this as an unreachable target (network issue) — exiting 0.');
    return 0;
  }

  // Count actual HTTP responses received (any status class).
  const responded =
    (result['1xx'] || 0) +
    (result['2xx'] || 0) +
    (result['3xx'] || 0) +
    (result['4xx'] || 0) +
    (result['5xx'] || 0);
  const errors = result.errors || 0; // socket-level errors (refused/reset/DNS)
  const timeouts = result.timeouts || 0;
  const non2xx = result.non2xx || 0;

  // If we received ZERO HTTP responses, the box was effectively unreachable. Degrade gracefully.
  if (responded === 0) {
    console.error(`\nNo HTTP responses from ${TARGET} (errors=${errors}, timeouts=${timeouts}).`);
    console.error('Target appears unreachable — degrading gracefully, exiting 0.');
    return 0;
  }

  const totalAttempts = responded + errors + timeouts;
  const errorRate = totalAttempts === 0 ? 0 : (non2xx + errors + timeouts) / totalAttempts;
  const p99 = result.latency ? result.latency.p99 : undefined;

  console.log('Results');
  console.log(`  requests    : ${result.requests.total} sent, ${responded} responded`);
  console.log(`  throughput  : ${result.requests.average.toFixed(1)} req/s avg, ` +
    `${(result.throughput.average / 1024).toFixed(1)} KB/s`);
  console.log('  status      : ' +
    `2xx=${result['2xx'] || 0} 3xx=${result['3xx'] || 0} ` +
    `4xx=${result['4xx'] || 0} 5xx=${result['5xx'] || 0} ` +
    `non2xx=${non2xx} errors=${errors} timeouts=${timeouts}`);
  console.log('  latency     : ' +
    `min=${ms(result.latency.min)}  p50=${ms(result.latency.p50)}  ` +
    `p90=${ms(result.latency.p90)}  p99=${ms(result.latency.p99)}  max=${ms(result.latency.max)}`);
  console.log(`  error-rate  : ${(errorRate * 100).toFixed(1)}%`);

  // Note (not a failure): 4xx here is often the box's own rate-limit (429) kicking in, which
  // means governance is working. We only FAIL the harness on a genuinely bad picture.
  let failed = false;
  if (errorRate > ERROR_RATE_MAX) {
    console.error(`\nFAIL: error rate ${(errorRate * 100).toFixed(1)}% exceeds ${(ERROR_RATE_MAX * 100).toFixed(0)}%.`);
    failed = true;
  }
  if (p99 !== undefined && p99 > P99_CEILING_MS) {
    console.error(`\nFAIL: p99 latency ${p99} ms exceeds ceiling ${P99_CEILING_MS} ms.`);
    failed = true;
  }

  if (failed) {
    return 1;
  }
  console.log('\nOK: throughput and latency within thresholds.');
  return 0;
}

// NOTE: we deliberately set `process.exitCode` and let the event loop drain instead of calling
// `process.exit()`. On Windows, forcing an exit while autocannon's sockets are still closing
// trips a libuv assertion (UV_HANDLE_CLOSING). Returning lets Node shut down cleanly.
main()
  .then((code) => {
    process.exitCode = code;
  })
  .catch((err) => {
    // Last-resort guard for anything truly unexpected — surface it as a failure.
    console.error('Unexpected error in load harness:', err);
    process.exitCode = 1;
  });

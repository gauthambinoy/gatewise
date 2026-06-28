# Auvex performance & contract harness

Two independent things live here:

1. **Load / performance harness** — measure throughput and latency of the gateway.
2. **Contract test** — guard against OpenAPI ↔ SDK drift (no network needed).

```
perf/
  load.k6.js          # k6 load test (staged smoke / load / spike) — needs k6 installed
  load.mjs            # pure-Node load test (autocannon) — needs only Node 18+
  contract.test.mjs   # OpenAPI <-> JS-SDK drift guard (node --test, no network)
  package.json        # deps: autocannon, js-yaml
```

```bash
cd perf
npm install
```

---

## 1. Load test

You have two ways to run it. They hit the same gateway; pick based on what you have installed.

### a) Pure-Node (recommended here) — `load.mjs`

No external tooling beyond Node 18+ and `npm install`. Uses
[`autocannon`](https://github.com/mcollina/autocannon).

```bash
node load.mjs
```

By default it fires a **short, low-rate** burst at the public `GET /auth/config` endpoint on
the live demo box and prints throughput + latency percentiles. It exits non-zero if the error
rate is high or p99 exceeds a (generous) ceiling. If the box is unreachable it degrades
gracefully and exits 0 with a clear message (a network blip must not fail CI).

Tunables (env vars, all optional):

| var           | default                                   | meaning                                  |
| ------------- | ----------------------------------------- | ---------------------------------------- |
| `BASE_URL`    | `https://auvex.54.170.218.176.nip.io`     | gateway base URL                         |
| `API_KEY`     | _(none)_                                  | sent as `Bearer` if set (not needed for `/auth/config`) |
| `DURATION`    | `5`                                       | seconds to run                           |
| `CONNECTIONS` | `2`                                       | concurrent connections                   |
| `RATE`        | `8`                                       | max requests/sec across all connections  |
| `P99_CEILING` | `5000`                                    | fail if p99 latency (ms) exceeds this    |
| `ERROR_RATE`  | `0.5`                                     | fail if error rate exceeds this fraction |

Point it at a **local** stack and push harder:

```bash
BASE_URL=http://localhost:8080 DURATION=30 CONNECTIONS=20 RATE=200 node load.mjs
```

### b) k6 — `load.k6.js`

If you have [k6](https://k6.io) installed, this runs three staged scenarios back-to-back
(**smoke → load → spike**) with pass/fail thresholds (p95 latency, error rate).

```bash
# default URL, public endpoint only (no key -> chat leg is skipped)
k6 run load.k6.js

# local stack, with a key so it also exercises POST /v1/chat/completions
k6 run -e BASE_URL=http://localhost:8080 -e API_KEY=auvex_sk_... load.k6.js
```

Env: `BASE_URL` (default the live demo URL) and `API_KEY` (optional — enables the
chat-completions leg). Without a key, only the public `GET /auth/config` is exercised so the
run doesn't generate 401s.

### Local stack vs live URL

- **Local:** `docker compose up` in the repo root, then `BASE_URL=http://localhost:8080`.
- **Live demo:** the default URL (`https://auvex.54.170.218.176.nip.io`).

> **Honest note on the live URL.** The default points at a small, single-node **demo** box
> (an AWS `t3.medium`) that is **rate-limited (~120 req/min)** and shared with the demo. It is
> fine for a smoke/sanity check, but it is **not** a valid target for real load testing — you
> will just measure its rate limiter. For genuine capacity / soak / spike testing, stand up a
> **dedicated or staging** instance sized like production, point `BASE_URL` at it, and raise the
> `DURATION` / `CONNECTIONS` / `RATE` (node) or the scenario stage targets (k6).

---

## 2. Contract test

A no-network test that parses the real request paths out of the JS SDK source
(`sdk/js/src/client.ts`) and asserts each one exists — with the expected HTTP method — in
`docs/openapi.yaml`. It covers chat/completions, embeddings, images, moderations, usage, audit,
models and policies, and fails if a path is missing or renamed in either place.

```bash
node --test contract.test.mjs
```

This is the cheap early-warning for SDK ↔ API drift; wire it into CI.

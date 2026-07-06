# GateWise — UI walkthrough (captured from the live, running stack)

Captured with Playwright against the running console (`http://localhost:3000`), logged in
via the **"Try the live demo"** sandbox, which is seeded with a week of realistic governed
traffic (24 requests across 4 model families: OpenAI, Anthropic, Gemini).

- **Screenshots:** `demo-capture/screenshots/` (11 PNGs, every page, full-page)
- **Video walkthrough:** `demo-capture/videos/gatewise-walkthrough.webm` (plays in any browser or VLC)

## The 3 headline features (what to look at)

### 1. Redact + tamper-proof audit  —  `02-dashboard.png`, `03-audit-log.png`, `11-request-detail.png`
Every prompt's sensitive data is masked **before it leaves the gateway** — you can see
`‹US_SSN_REDACTED›`, `‹CREDIT_CARD_REDACTED›`, `‹EMAIL_REDACTED›` in the live prompts. Every
call is written to a **SHA-256 hash-chained audit log** — the request-detail view shows
*"Hash-chain verified — this record has not been altered"* with the entry hash. 11 of 24
requests had PII redacted; the chain is verifiable end-to-end.

### 2. Policy governance  —  `04-policies.png`, `05-models-routing.png`
Allow / deny / **redact** rules per data-type or model. The demo org blocks credit-card
prompts (3 requests blocked), redacts email/PII to external models, and the routing table
doubles as the model allow-list (anything not listed is refused).

### 3. Cost + usage visibility  —  `06-dashboard.png`/`06-usage-cost.png`, `07-users.png`
Per-model spend, per-verdict mix (allowed 10 / redacted 11 / blocked 3), per-leak-type
breakdown (cards, SSNs, API keys, phones, emails), total cost ($0.1342) and tokens (24,320),
all attributed per user/actor.

## Backend proof (separate from the UI)
`164 automated tests, 0 failures` across the governance engine — run `./gradlew clean build`.

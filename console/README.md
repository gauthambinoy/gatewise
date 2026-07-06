# GateWise Console

The React admin console for GateWise — a Vite + React 18 + TypeScript SPA that binds
to the gateway's REST API. It carries the dashboard, audit-log explorer (with
search and hash-chain verification), policy editor, model routing, usage & cost,
per-user metrics, members & roles, and API-key management.

## Develop

```bash
npm install
npm run dev        # http://localhost:5173 — proxies /v1 and /auth to the gateway on :8080
```

Sign in with an GateWise API key; it's stored only in the browser and sent as a bearer
token. Point the gateway at `:8080` first (see the repo root README).

## Build & ship

```bash
npm run typecheck  # tsc --noEmit
npm run build      # typecheck + production bundle into dist/
```

The `Dockerfile` builds the bundle and serves it with nginx, which also proxies
`/v1` and `/auth` to the `gateway` container — so the browser sees one origin (no
CORS). `docker compose up` (at the repo root) runs it alongside the gateway on
`:3000`.

## Design

The look is the project's own design system — tokens in `src/styles/tokens.css`
(light/dark), Inter + Tabler icons — ported from the design mockups in `design/`.
No UI kit; just the tokens and a few shared primitives in `src/components/ui.tsx`.

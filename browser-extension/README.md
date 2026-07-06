# GateWise Guard

A Manifest V3 browser extension that brings GateWise governance to **web-based AI tools**. When you are
about to send a prompt on a supported site, GateWise Guard screens the prompt text through your GateWise
gateway's native moderation endpoint and **warns or blocks** before the text leaves your machine if it
contains sensitive data (PII, secrets) or a prompt-injection pattern. It keeps a small local log of
findings so you can see what the guard caught.

The goal is simple: stop people from pasting confidential data into public AI tools by accident.

## What it does

- Detects the prompt input and send action on supported AI sites.
- On send, it grabs the prompt text and asks your GateWise gateway to screen it
  (`POST /v1/moderations`).
- If the gateway flags the prompt, an in-page modal shows **what was found** and offers
  **Send anyway / Cancel** (fail-open), or **blocks the send outright** (fail-closed).
- A capped local log (most recent 50 findings) feeds the toolbar popup.

## Supported sites

| Site    | Hosts                              |
| ------- | ---------------------------------- |
| ChatGPT | `chatgpt.com`, `chat.openai.com`   |
| Claude  | `claude.ai`                        |
| Gemini  | `gemini.google.com`                |

Each site can be toggled on or off independently on the options page.

## Install (load unpacked)

1. Open `chrome://extensions` (Chrome) or `edge://extensions` (Edge).
2. Enable **Developer mode** (top-right).
3. Click **Load unpacked** and select this `browser-extension` folder.
4. Pin **GateWise Guard** to the toolbar if you want quick access to the status popup.

The extension is plain TypeScript-flavoured JavaScript with no build step — the folder loads as-is.

### Granting access to your gateway

The content scripts are scoped to the AI sites listed above. The **gateway** call happens from the
background service worker, and the gateway URL is configurable, so its host is not (and cannot be)
hard-coded in the manifest. Two cases:

- **`http://localhost:8080`** (the default) works out of the box from the worker.
- A **remote gateway** (e.g. `https://gatewise.example.com`) needs host access. The manifest declares
  `optional_host_permissions` for `http://*/*` and `https://*/*`; grant access when prompted, or in
  `chrome://extensions` → GateWise Guard → **Details** → **Site access**, add your gateway origin. This
  keeps the always-on permission set narrow (just the AI sites) while still letting you point at any
  gateway you control.

## Configure

Open the options page (popup → **Options**, or the extensions page → **Details** → **Extension
options**) and set:

- **Gateway base URL** — your GateWise gateway origin (default `http://localhost:8080`). Validated as an
  `http`/`https` URL.
- **API key** — sent as `Authorization: Bearer <key>`. Stored only in `chrome.storage.local` on this
  device.
- **Fail-closed** toggle — see below.
- **Per-site** toggles — enable/disable screening per supported site.
- **Test connection** — probes `GET /v1/models` with your key and reports reachable / unauthorised /
  unreachable, so you can tell a wrong URL from a wrong key.

### Fail-open vs fail-closed

- **Fail-open (default).** If the gateway is unreachable or errors, the prompt is **allowed** — an
  outage never stops your work. Real findings produce a **warning** you can override.
- **Fail-closed.** If the gateway can't return a verdict, the prompt is **blocked**, and findings are
  blocked outright with no override. Stricter posture for regulated environments.

## How it talks to the gateway

```
POST {baseUrl}/v1/moderations
Authorization: Bearer <apiKey>
Content-Type: application/json

{ "input": "<the prompt text>" }
```

Response envelope:

```json
{
  "flagged": true,
  "sensitiveData": { "email": 1, "api_key": 2 },
  "injection": ["instruction_override"]
}
```

## Maintenance note: selectors drift

AI sites change their DOM frequently (redesigns, A/B tests, framework swaps). All site-specific
selectors and send behaviour are centralised in a single **`SITE_ADAPTERS`** map at the top of
`src/content.js`. Each adapter uses an **ordered list of selector fallbacks** — the first that
matches wins — to stay resilient across gradual rollouts. If a site stops being intercepted, update
that one map; nothing else in the content script is site-specific.

The screening interception hooks both **Enter-to-send** (without Shift) and **clicking the send
button**, in the capture phase, so it runs before the site's own handlers. When a prompt is cleared
to send, the original action is faithfully re-dispatched.

## Privacy

Your prompt text is sent **only** to the GateWise gateway you configure — nowhere else. There is no
telemetry, no analytics, and no third-party endpoint. The findings log is stored locally in
`chrome.storage.local` and never leaves the browser. Clearing it (popup → **Clear log**) removes it.

## Architecture

- `src/screen.js` — pure, dependency-free screening core: builds the moderation request, parses the
  envelope, and makes the allow/warn/block decision under the fail-open/closed policy. No `chrome.*`
  or DOM access, so it is unit-tested directly in Node.
- `src/background.js` — MV3 service worker. The only place that performs network I/O to the gateway.
  Routes content-script messages, applies settings, and maintains the capped log.
- `src/content.js` — runs in the page, intercepts sends, and renders the warning modal in a shadow
  root (so the host page's CSS can't hide or restyle it).
- `popup/` — toolbar popup: connection status, recent findings, link to options.
- `options/` — settings page with URL validation and a Test connection button.

## Tests

The screening core has unit tests using Node's built-in test runner, with a mocked `fetch` and no
real network. They cover flagged→block, clean→allow, gateway-error fail-open vs fail-closed, and
envelope parsing.

```
node --test
```

(Run from the `browser-extension` directory. Requires Node 18+. No dependencies to install.)

## A note on icons

The icons are clean monochrome SVG shields (`icons/icon-16.svg`, `-48`, `-128`) with no external
assets. SVG renders perfectly in the popup and options pages. Chrome's **toolbar** action icon
historically prefers raster (PNG) and may fall back to a default glyph for the SVG on some versions;
this does not affect functionality. If you want a guaranteed toolbar glyph, export the SVGs to PNG at
16/48/128 px and swap the `icons` / `action.default_icon` references in `manifest.json`.

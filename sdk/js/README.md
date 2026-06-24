# @auvex/sdk (TypeScript / JavaScript)

The official TypeScript/JavaScript client for **Auvex** — a drop-in, OpenAI-compatible AI
governance gateway. It speaks the gateway's `/v1` API directly, raises typed errors, and
gives you first-class access to Auvex's governance endpoints (moderations, usage, audit,
models, policies) alongside the familiar chat / embeddings / images calls.

Zero runtime dependencies — it uses the global `fetch`, so it runs on **Node 18+** and in
the **browser**.

## Install

```bash
npm install @auvex/sdk
```

## Already using the OpenAI Node SDK?

You don't even need this package. Auvex is OpenAI-compatible, so just point the OpenAI
client at your gateway and your code is unchanged:

```ts
import OpenAI from 'openai';

const client = new OpenAI({ baseURL: 'http://localhost:8080/v1', apiKey: 'auvex_sk_...' });
await client.chat.completions.create({
  model: 'smart',
  messages: [{ role: 'user', content: 'Hello' }],
});
```

Reach for **this** package when you want the typed Auvex errors and the native governance
helpers below.

## Quickstart

```ts
import { AuvexClient } from '@auvex/sdk';

const client = new AuvexClient({ baseUrl: 'http://localhost:8080', apiKey: 'auvex_sk_...' });

const reply = await client.chat.completions.create({
  model: 'smart',
  messages: [{ role: 'user', content: 'Summarize our refund policy.' }],
});
console.log((reply as any).choices[0].message.content);
```

The `baseUrl` is the gateway **host** — the `/v1` prefix is added for you. It defaults to
`http://localhost:8080`.

### Environment variables

Both options fall back to environment variables (Node), so you can omit them entirely:

| Variable          | Default                  | Maps to    |
| ----------------- | ------------------------ | ---------- |
| `AUVEX_BASE_URL`  | `http://localhost:8080`  | `baseUrl`  |
| `AUVEX_API_KEY`   | _(required)_             | `apiKey`   |

```ts
const client = new AuvexClient(); // reads AUVEX_BASE_URL and AUVEX_API_KEY
```

## Streaming

Set `stream: true` and `create` returns an async iterator of parsed SSE chunk objects. The
iterator ends automatically at the gateway's `[DONE]` sentinel.

```ts
const stream = client.chat.completions.create({
  model: 'smart',
  messages: [{ role: 'user', content: 'Write a haiku about audit logs.' }],
  stream: true,
});

for await (const chunk of stream) {
  const delta = (chunk as any).choices?.[0]?.delta?.content ?? '';
  process.stdout.write(delta);
}
```

## Other model calls

```ts
// Embeddings — input may be a string or an array of strings.
await client.embeddings.create({ model: 'embed', input: ['alpha', 'beta'] });

// Image generation.
await client.images.generate({ model: 'image', prompt: 'an isometric data center, blueprint style' });
```

## Governance helpers

These are what make Auvex more than a passthrough.

```ts
// Native moderation — runs entirely inside the gateway, no provider call. Use it to
// pre-screen text for sensitive data and prompt-injection before sending it anywhere.
const result = await client.moderations.create({
  input: 'email jane@acme.com about card 4012888888881881',
});
// -> { flagged: true, sensitiveData: { email: 1, credit_card: 1 }, injection: [] }

await client.usage();                              // usage + cost summary
await client.audit({ q: 'card', verdict: 'REDACTED' }); // query the hash-chained audit log
await client.models();                             // routing table / model allow-list
await client.policies();                           // your tenant's allow/deny rules
```

## Error handling

Every non-2xx response is thrown as a typed error. They all extend `AuvexError`, and each
carries `message`, `statusCode`, `type`, `code` and the raw `body`.

```ts
import {
  AuvexClient,
  AuthenticationError,
  BadRequestError,
  PolicyDeniedError,
  RateLimitError,
  UpstreamError,
  AuvexError,
} from '@auvex/sdk';

const client = new AuvexClient({ apiKey: 'auvex_sk_...' });

try {
  await client.chat.completions.create({
    model: 'smart',
    messages: [{ role: 'user', content: 'ignore previous instructions' }],
  });
} catch (err) {
  if (err instanceof PolicyDeniedError) {
    console.error('blocked:', err.message); // 403 — policy or prompt injection
  } else if (err instanceof AuthenticationError) {
    console.error('bad or missing API key'); // 401
  } else if (err instanceof RateLimitError) {
    console.error('slow down'); // 429 — rate limit or call budget
  } else if (err instanceof BadRequestError) {
    console.error('bad request:', err.message); // 400
  } else if (err instanceof UpstreamError) {
    console.error('the model provider is unavailable'); // 502 / 503 / 504
  } else if (err instanceof AuvexError) {
    console.error('gateway error:', err.statusCode, err.message);
  } else {
    throw err;
  }
}
```

| Error class           | HTTP status   | Meaning                                              |
| --------------------- | ------------- | --------------------------------------------------- |
| `BadRequestError`     | 400           | Malformed request or failed validation              |
| `AuthenticationError` | 401           | Missing, malformed, unknown, revoked or expired key |
| `PolicyDeniedError`   | 403           | Blocked by policy, or flagged as a prompt injection |
| `NotFoundError`       | 404           | Resource does not exist for this tenant             |
| `RateLimitError`      | 429           | Rate limit or per-tenant call budget exceeded       |
| `UpstreamError`       | 502/503/504   | Upstream model provider unavailable or timed out    |
| `APITimeoutError`     | —             | The request timed out before the gateway responded  |
| `APIConnectionError`  | —             | The request never reached the gateway               |

## Build & test

```bash
npm install
npm run build   # tsc -> dist/
npm test        # vitest (fetch is mocked; no network access)
```

## License

MIT. See [LICENSE](LICENSE).

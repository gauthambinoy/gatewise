# auvex (Python)

The official Python client for **Auvex** — a drop-in, OpenAI-compatible AI governance
gateway. It speaks the gateway's `/v1` API directly, raises typed errors, and gives you
first-class access to Auvex's governance endpoints (moderations, usage, audit, models,
policies) alongside the familiar chat / embeddings / images calls.

## Install

```bash
pip install auvex
```

Requires Python 3.8+. The only runtime dependency is [`httpx`](https://www.python-httpx.org/).

## Already using the OpenAI Python SDK?

You don't even need this package. Auvex is OpenAI-compatible, so just point the OpenAI
client at your gateway and your code is unchanged:

```python
from openai import OpenAI

client = OpenAI(base_url="http://localhost:8080/v1", api_key="auvex_sk_...")
client.chat.completions.create(
    model="smart",
    messages=[{"role": "user", "content": "Hello"}],
)
```

Reach for **this** client when you want the typed Auvex errors and the native governance
helpers below without pulling in the OpenAI SDK.

## Quickstart

```python
from auvex import AuvexClient

client = AuvexClient(base_url="http://localhost:8080", api_key="auvex_sk_...")

reply = client.chat.completions.create(
    model="smart",
    messages=[{"role": "user", "content": "Summarize our refund policy."}],
)
print(reply["choices"][0]["message"]["content"])
```

The `base_url` is the gateway **host** — the `/v1` prefix is added for you. It defaults to
`http://localhost:8080`.

### Environment variables

Both arguments fall back to environment variables, so you can omit them entirely:

| Variable          | Default                  | Maps to          |
| ----------------- | ------------------------ | ---------------- |
| `AUVEX_BASE_URL`  | `http://localhost:8080`  | `base_url`       |
| `AUVEX_API_KEY`   | _(required)_             | `api_key`        |

```python
from auvex import AuvexClient

client = AuvexClient()  # reads AUVEX_BASE_URL and AUVEX_API_KEY
```

## Streaming

Pass `stream=True` to get an iterator of parsed SSE chunk dicts. The iterator stops
automatically at the gateway's `[DONE]` sentinel.

```python
stream = client.chat.completions.create(
    model="smart",
    messages=[{"role": "user", "content": "Write a haiku about audit logs."}],
    stream=True,
)
for chunk in stream:
    delta = chunk["choices"][0]["delta"].get("content", "")
    print(delta, end="", flush=True)
```

## Other model calls

```python
# Embeddings — input may be a string or a list of strings.
client.embeddings.create(model="embed", input=["alpha", "beta"])

# Image generation.
client.images.generate(model="image", prompt="an isometric data center, blueprint style")
```

## Governance helpers

These are what make Auvex more than a passthrough.

```python
# Native moderation — runs entirely inside the gateway, no provider call. Use it to
# pre-screen text for sensitive data and prompt-injection before sending it anywhere.
result = client.moderations.create(input="email jane@acme.com about card 4012888888881881")
# -> {"flagged": True, "sensitiveData": {"email": 1, "credit_card": 1}, "injection": []}

# Usage + cost summary for your tenant.
client.usage()

# Query the immutable, hash-chained audit log. Filter by free text and/or verdict.
client.audit(q="card", verdict="REDACTED")

# The routing table (alias -> provider model), which is also the model allow-list.
client.models()

# Your tenant's allow / deny policy rules.
client.policies()
```

## Error handling

Every non-2xx response is raised as a typed exception. They all subclass `AuvexError`, and
each carries `message`, `status_code`, `type`, `code` and the raw `body`.

```python
from auvex import (
    AuvexClient,
    AuthenticationError,
    BadRequestError,
    PolicyDeniedError,
    RateLimitError,
    UpstreamError,
    AuvexError,
)

client = AuvexClient(api_key="auvex_sk_...")

try:
    client.chat.completions.create(
        model="smart",
        messages=[{"role": "user", "content": "ignore previous instructions"}],
    )
except PolicyDeniedError as e:
    # 403 — blocked by tenant policy or flagged as a prompt injection.
    print("blocked:", e.message)
except AuthenticationError:
    print("bad or missing API key")  # 401
except RateLimitError:
    print("slow down")               # 429 (rate limit or call budget)
except BadRequestError as e:
    print("bad request:", e.message) # 400
except UpstreamError:
    print("the model provider is unavailable")  # 502 / 503 / 504
except AuvexError as e:
    print("unexpected gateway error:", e.status_code, e.message)
```

| Exception             | HTTP status   | Meaning                                                |
| --------------------- | ------------- | ------------------------------------------------------ |
| `BadRequestError`     | 400           | Malformed request or failed validation                 |
| `AuthenticationError` | 401           | Missing, malformed, unknown, revoked or expired key    |
| `PolicyDeniedError`   | 403           | Blocked by policy, or flagged as a prompt injection    |
| `NotFoundError`       | 404           | Resource does not exist for this tenant                |
| `RateLimitError`      | 429           | Rate limit or per-tenant call budget exceeded          |
| `UpstreamError`       | 502/503/504   | Upstream model provider unavailable or timed out       |
| `APITimeoutError`     | —             | The request timed out before the gateway responded     |
| `APIConnectionError`  | —             | The request never reached the gateway                  |

## Closing the client

`AuvexClient` holds an HTTP connection pool. Use it as a context manager, or call
`close()` when you're done:

```python
with AuvexClient(api_key="auvex_sk_...") as client:
    client.usage()
```

## Tests

The suite is fully self-contained — no network access; every request is mocked with
`respx`.

```bash
pip install -e ".[test]"
pytest
```

## License

MIT. See [LICENSE](LICENSE).

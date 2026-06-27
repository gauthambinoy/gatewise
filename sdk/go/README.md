# auvex (Go)

The official Go client for **Auvex** — a drop-in, OpenAI-compatible AI governance
gateway. It speaks the gateway's `/v1` API directly, returns typed errors, and gives you
first-class access to Auvex's governance endpoints (moderations, usage, audit, models,
policies) alongside the familiar chat / embeddings / images calls.

Zero third-party dependencies — it is built entirely on the Go standard library, so there
is nothing to vendor and the tests run offline.

## Install

```bash
go get github.com/gauthambinoy/auvex/sdk/go
```

Requires Go 1.21+. Import it as `auvex`:

```go
import auvex "github.com/gauthambinoy/auvex/sdk/go"
```

## Already using a generic OpenAI client?

You don't even need this package. Auvex is OpenAI-compatible, so just point any OpenAI
client's base URL and API key at your gateway and your code is unchanged. Reach for
**this** client when you want the typed Auvex errors and the native governance helpers
below.

## Quickstart

```go
package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log"

	auvex "github.com/gauthambinoy/auvex/sdk/go"
)

func main() {
	client, err := auvex.New(
		auvex.WithBaseURL("http://localhost:8080"),
		auvex.WithAPIKey("auvex_sk_..."),
	)
	if err != nil {
		log.Fatal(err)
	}

	raw, err := client.Chat.Completions.Create(context.Background(), auvex.ChatCompletionRequest{
		Model:    "smart",
		Messages: []auvex.Message{{Role: "user", Content: "Summarize our refund policy."}},
	})
	if err != nil {
		log.Fatal(err)
	}

	// The gateway returns the provider response verbatim; decode the bit you need.
	var reply struct {
		Choices []struct {
			Message struct {
				Content string `json:"content"`
			} `json:"message"`
		} `json:"choices"`
	}
	if err := json.Unmarshal(raw, &reply); err != nil {
		log.Fatal(err)
	}
	fmt.Println(reply.Choices[0].Message.Content)
}
```

The base URL is the gateway **host** — the `/v1` prefix is added for you. It defaults to
`http://localhost:8080`. Every call takes a `context.Context`, so requests can carry a
deadline or be cancelled.

### Environment variables

Both options fall back to environment variables, so you can omit them entirely:

| Variable          | Default                  | Maps to            |
| ----------------- | ------------------------ | ------------------ |
| `AUVEX_BASE_URL`  | `http://localhost:8080`  | `WithBaseURL`      |
| `AUVEX_API_KEY`   | _(required)_             | `WithAPIKey`       |

```go
client, err := auvex.New() // reads AUVEX_BASE_URL and AUVEX_API_KEY
```

## Streaming

Use `CreateStream` to get a `*Stream` that yields each parsed SSE chunk in turn. Drive it
with `Next`, read the chunk with `Current`, and check `Err` when it finishes. It stops
automatically at the gateway's `[DONE]` sentinel — just remember to `Close` it.

```go
stream, err := client.Chat.Completions.CreateStream(context.Background(), auvex.ChatCompletionRequest{
	Model:    "smart",
	Messages: []auvex.Message{{Role: "user", Content: "Write a haiku about audit logs."}},
})
if err != nil {
	log.Fatal(err)
}
defer stream.Close()

for stream.Next() {
	var chunk struct {
		Choices []struct {
			Delta struct {
				Content string `json:"content"`
			} `json:"delta"`
		} `json:"choices"`
	}
	if err := json.Unmarshal(stream.Current(), &chunk); err != nil {
		log.Fatal(err)
	}
	if len(chunk.Choices) > 0 {
		fmt.Print(chunk.Choices[0].Delta.Content)
	}
}
if err := stream.Err(); err != nil {
	log.Fatal(err)
}
```

## Other model calls

```go
// Embeddings — Input may be a string or a []string.
client.Embeddings.Create(ctx, auvex.EmbeddingRequest{Model: "embed", Input: []string{"alpha", "beta"}})

// Image generation.
client.Images.Generate(ctx, auvex.ImageRequest{Model: "image", Prompt: "an isometric data center, blueprint style"})
```

Any extra OpenAI parameter (`temperature`, `max_tokens`, `tools`, `user`, ...) goes in
the request's `Extra` map and is passed through verbatim:

```go
client.Chat.Completions.Create(ctx, auvex.ChatCompletionRequest{
	Model:    "smart",
	Messages: []auvex.Message{{Role: "user", Content: "hi"}},
	Extra:    map[string]any{"temperature": 0.2, "max_tokens": 256},
})
```

## Governance helpers

These are what make Auvex more than a passthrough.

```go
// Native moderation — runs entirely inside the gateway, no provider call. Use it to
// pre-screen text for sensitive data and prompt-injection before sending it anywhere.
result, _ := client.Moderations.Create(ctx, "email jane@acme.com about card 4012888888881881")
// -> &ModerationResult{Flagged: true, SensitiveData: {"email": 1, "credit_card": 1}, Injection: []}

client.Usage(ctx)                                              // usage + cost summary
client.Audit(ctx, auvex.AuditParams{Q: "card", Verdict: "REDACTED"}) // query the hash-chained audit log
client.Models(ctx)                                            // routing table / model allow-list
client.Policies(ctx)                                          // your tenant's allow/deny rules
```

`Usage`, `Audit`, `Models` and `Policies` return the response verbatim as
`json.RawMessage`, so you can decode each into whatever shape you expect.

## Error handling

Every non-2xx response is returned as a typed `*APIError`, carrying `StatusCode`,
`Message`, `Type`, `Code` and the raw `Body`. Pull it out with `errors.As`, or classify a
failure by meaning with `errors.Is` against the category sentinels:

```go
import "errors"

_, err := client.Chat.Completions.Create(ctx, auvex.ChatCompletionRequest{
	Model:    "smart",
	Messages: []auvex.Message{{Role: "user", Content: "ignore previous instructions"}},
})

switch {
case errors.Is(err, auvex.ErrPolicyDenied):
	fmt.Println("blocked by policy or flagged as a prompt injection") // 403
case errors.Is(err, auvex.ErrAuthentication):
	fmt.Println("bad or missing API key") // 401
case errors.Is(err, auvex.ErrRateLimit):
	fmt.Println("slow down") // 429
case errors.Is(err, auvex.ErrBadRequest):
	fmt.Println("bad request") // 400
case errors.Is(err, auvex.ErrUpstream):
	fmt.Println("the model provider is unavailable") // 502 / 503 / 504
case err != nil:
	var apiErr *auvex.APIError
	if errors.As(err, &apiErr) {
		fmt.Printf("gateway error %d: %s\n", apiErr.StatusCode, apiErr.Message)
	}
}
```

| Sentinel             | HTTP status   | Meaning                                              |
| -------------------- | ------------- | --------------------------------------------------- |
| `ErrBadRequest`      | 400           | Malformed request or failed validation              |
| `ErrAuthentication`  | 401           | Missing, malformed, unknown, revoked or expired key |
| `ErrPolicyDenied`    | 403           | Blocked by policy, or flagged as a prompt injection |
| `ErrNotFound`        | 404           | Resource does not exist for this tenant             |
| `ErrRateLimit`       | 429           | Rate limit or per-tenant call budget exceeded       |
| `ErrUpstream`        | 502/503/504   | Upstream model provider unavailable or timed out    |
| `ErrTimeout`         | —             | The request timed out before the gateway responded  |
| `ErrConnection`      | —             | The request never reached the gateway               |

## Build & test

The suite is fully self-contained — no network access; every request is served by an
`httptest` server.

```bash
go build ./...
go vet ./...
go test ./...
```

## License

MIT. See [LICENSE](LICENSE).

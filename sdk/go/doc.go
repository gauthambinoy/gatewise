// Package gatewise is the official Go client for GateWise, a drop-in, OpenAI-compatible
// AI governance gateway.
//
// The gateway is OpenAI-compatible, so if you are already using a generic OpenAI
// client you can simply point its base URL and API key at your gateway and your code
// is unchanged. This package exists for callers who want typed errors and first-class
// access to GateWise's governance endpoints (moderations, usage, audit, models, policies)
// alongside the familiar chat / embeddings / images calls.
//
// The base URL is the gateway host — the /v1 prefix is added for you — and both the
// base URL and the API key fall back to the GATEWISE_BASE_URL and GATEWISE_API_KEY
// environment variables when not passed explicitly.
//
// Quickstart:
//
//	client, err := gatewise.New(
//		gatewise.WithBaseURL("http://localhost:8080"),
//		gatewise.WithAPIKey("gatewise_sk_..."),
//	)
//	if err != nil {
//		log.Fatal(err)
//	}
//
//	raw, err := client.Chat.Completions.Create(ctx, gatewise.ChatCompletionRequest{
//		Model:    "smart",
//		Messages: []gatewise.Message{{Role: "user", Content: "Hello"}},
//	})
//
// Every method takes a context.Context so calls can be cancelled or given a deadline,
// and every non-2xx response is returned as a typed *APIError.
package gatewise

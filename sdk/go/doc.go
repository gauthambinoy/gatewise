// Package auvex is the official Go client for Auvex, a drop-in, OpenAI-compatible
// AI governance gateway.
//
// The gateway is OpenAI-compatible, so if you are already using a generic OpenAI
// client you can simply point its base URL and API key at your gateway and your code
// is unchanged. This package exists for callers who want typed errors and first-class
// access to Auvex's governance endpoints (moderations, usage, audit, models, policies)
// alongside the familiar chat / embeddings / images calls.
//
// The base URL is the gateway host — the /v1 prefix is added for you — and both the
// base URL and the API key fall back to the AUVEX_BASE_URL and AUVEX_API_KEY
// environment variables when not passed explicitly.
//
// Quickstart:
//
//	client, err := auvex.New(
//		auvex.WithBaseURL("http://localhost:8080"),
//		auvex.WithAPIKey("auvex_sk_..."),
//	)
//	if err != nil {
//		log.Fatal(err)
//	}
//
//	raw, err := client.Chat.Completions.Create(ctx, auvex.ChatCompletionRequest{
//		Model:    "smart",
//		Messages: []auvex.Message{{Role: "user", Content: "Hello"}},
//	})
//
// Every method takes a context.Context so calls can be cancelled or given a deadline,
// and every non-2xx response is returned as a typed *APIError.
package auvex

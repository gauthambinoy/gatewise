package auvex

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"testing"
)

// sseHandler replies with the given SSE text and a text/event-stream content type, while
// recording the request body so a test can confirm streaming was advertised.
func sseHandler(cap *captured, sse string) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if cap != nil {
			cap.body, _ = io.ReadAll(r.Body)
		}
		w.Header().Set("Content-Type", "text/event-stream")
		w.WriteHeader(200)
		_, _ = io.WriteString(w, sse)
	}
}

// collect drains a stream into a slice of decoded chunks, failing the test on a stream
// error.
func collect(t *testing.T, stream *Stream) []map[string]any {
	t.Helper()
	var chunks []map[string]any
	for stream.Next() {
		var chunk map[string]any
		if err := json.Unmarshal(stream.Current(), &chunk); err != nil {
			t.Fatalf("decode chunk: %v", err)
		}
		chunks = append(chunks, chunk)
	}
	if err := stream.Err(); err != nil {
		t.Fatalf("stream error: %v", err)
	}
	return chunks
}

// deltaContent digs the streamed token out of an OpenAI-style chunk.
func deltaContent(t *testing.T, chunk map[string]any) string {
	t.Helper()
	choices, ok := chunk["choices"].([]any)
	if !ok || len(choices) == 0 {
		t.Fatalf("chunk has no choices: %v", chunk)
	}
	choice, _ := choices[0].(map[string]any)
	delta, _ := choice["delta"].(map[string]any)
	content, _ := delta["content"].(string)
	return content
}

func TestStreamingParsesSSE(t *testing.T) {
	sse := "data: {\"choices\":[{\"delta\":{\"content\":\"Hel\"}}]}\n\n" +
		"data: {\"choices\":[{\"delta\":{\"content\":\"lo\"}}]}\n\n" +
		"data: [DONE]\n\n"
	var cap captured
	client, srv := newTestClient(t, sseHandler(&cap, sse))
	defer srv.Close()

	stream, err := client.Chat.Completions.CreateStream(context.Background(), ChatCompletionRequest{
		Model:    "smart",
		Messages: []Message{{Role: "user", Content: "hi"}},
	})
	if err != nil {
		t.Fatalf("CreateStream: %v", err)
	}
	defer stream.Close()

	chunks := collect(t, stream)
	if len(chunks) != 2 {
		t.Fatalf("got %d chunks, want 2", len(chunks))
	}
	if got := deltaContent(t, chunks[0]); got != "Hel" {
		t.Errorf("chunk 0 content = %q, want Hel", got)
	}
	if got := deltaContent(t, chunks[1]); got != "lo" {
		t.Errorf("chunk 1 content = %q, want lo", got)
	}

	// The request advertised streaming to the gateway.
	var body map[string]any
	if err := json.Unmarshal(cap.body, &body); err != nil {
		t.Fatalf("decode body: %v", err)
	}
	if body["stream"] != true {
		t.Errorf("stream = %v, want true", body["stream"])
	}
}

func TestStreamingSkipsBlankAndMalformed(t *testing.T) {
	sse := "\n" +
		"data: \n\n" +
		"data: not-json\n\n" +
		"data: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}\n\n" +
		"data: [DONE]\n\n"
	client, srv := newTestClient(t, sseHandler(nil, sse))
	defer srv.Close()

	stream, err := client.Chat.Completions.CreateStream(context.Background(), ChatCompletionRequest{
		Model:    "smart",
		Messages: []Message{{Role: "user", Content: "hi"}},
	})
	if err != nil {
		t.Fatalf("CreateStream: %v", err)
	}
	defer stream.Close()

	chunks := collect(t, stream)
	if len(chunks) != 1 {
		t.Fatalf("got %d chunks, want 1", len(chunks))
	}
	if got := deltaContent(t, chunks[0]); got != "ok" {
		t.Errorf("content = %q, want ok", got)
	}
}

func TestStreamingHandlesTrailingChunkWithoutNewline(t *testing.T) {
	// A final data line not terminated by a blank line should still be yielded.
	sse := "data: {\"choices\":[{\"delta\":{\"content\":\"tail\"}}]}"
	client, srv := newTestClient(t, sseHandler(nil, sse))
	defer srv.Close()

	stream, err := client.Chat.Completions.CreateStream(context.Background(), ChatCompletionRequest{
		Model:    "smart",
		Messages: []Message{{Role: "user", Content: "hi"}},
	})
	if err != nil {
		t.Fatalf("CreateStream: %v", err)
	}
	defer stream.Close()

	chunks := collect(t, stream)
	if len(chunks) != 1 {
		t.Fatalf("got %d chunks, want 1", len(chunks))
	}
	if got := deltaContent(t, chunks[0]); got != "tail" {
		t.Errorf("content = %q, want tail", got)
	}
}

func TestStreamingErrorStatusRaises(t *testing.T) {
	client, srv := newTestClient(t, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(403)
		_, _ = io.WriteString(w, `{"error":{"message":"denied","type":"policy_violation"}}`)
	}))
	defer srv.Close()

	_, err := client.Chat.Completions.CreateStream(context.Background(), ChatCompletionRequest{
		Model:    "smart",
		Messages: []Message{{Role: "user", Content: "hi"}},
	})
	if err == nil {
		t.Fatal("expected an error from a 403 streaming response, got nil")
	}
	if !errors.Is(err, ErrPolicyDenied) {
		t.Errorf("errors.Is(err, ErrPolicyDenied) = false; err = %v", err)
	}
}

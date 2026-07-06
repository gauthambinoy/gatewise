package gatewise

import (
	"context"
	"encoding/json"
	"net/http"
)

// Message is an OpenAI-style chat message. Content is left open (any) so multimodal
// payloads pass through unchanged, and Extra carries any additional fields a particular
// message shape needs (for example tool_calls on an assistant message).
type Message struct {
	// Role is the speaker, e.g. "system", "user", "assistant" or "tool".
	Role string
	// Content is the message body — a string for plain text, or any JSON-serializable
	// value for multimodal content.
	Content any
	// Name is the optional author name, sent only when set.
	Name string
	// Extra holds any other fields to merge into the message object verbatim.
	Extra map[string]any
}

// MarshalJSON serializes the message, merging Extra and then letting the typed fields
// win, so role and content are always authoritative.
func (m Message) MarshalJSON() ([]byte, error) {
	out := make(map[string]any, len(m.Extra)+3)
	for k, v := range m.Extra {
		out[k] = v
	}
	out["role"] = m.Role
	out["content"] = m.Content
	if m.Name != "" {
		out["name"] = m.Name
	}
	return json.Marshal(out)
}

// ChatCompletionRequest is the request for a chat completion. Beyond the required Model
// and Messages, any other OpenAI chat parameter (temperature, max_tokens, tools, user,
// ...) goes in Extra and is passed through to the gateway verbatim. The Stream field is
// managed by the client — use Create for a single response and CreateStream for an SSE
// stream — so setting it by hand has no effect.
type ChatCompletionRequest struct {
	// Model is a model alias the gateway routes (e.g. "smart", "fast").
	Model string
	// Messages is the OpenAI-style message list.
	Messages []Message
	// Extra holds any other OpenAI chat parameter, merged into the request body.
	Extra map[string]any

	// stream is set internally by Create / CreateStream.
	stream bool
}

// MarshalJSON serializes the request, merging Extra and then letting the typed fields
// (model, messages, stream) win.
func (r ChatCompletionRequest) MarshalJSON() ([]byte, error) {
	out := make(map[string]any, len(r.Extra)+3)
	for k, v := range r.Extra {
		out[k] = v
	}
	out["model"] = r.Model
	out["messages"] = r.Messages
	out["stream"] = r.stream
	return json.Marshal(out)
}

// ChatService is the chat namespace, holding Completions.
type ChatService struct {
	// Completions is the OpenAI-compatible chat-completions endpoint.
	Completions *ChatCompletionsService
}

// ChatCompletionsService accesses POST /v1/chat/completions.
type ChatCompletionsService struct {
	client *Client
}

// Create makes a single (non-streaming) chat completion. The gateway's response is
// returned verbatim as json.RawMessage; decode it into whatever shape you expect, e.g.
// the OpenAI completion object.
func (s *ChatCompletionsService) Create(
	ctx context.Context,
	req ChatCompletionRequest,
) (json.RawMessage, error) {
	req.stream = false
	return s.client.request(ctx, http.MethodPost, "chat/completions", req, nil)
}

// CreateStream makes a streaming chat completion and returns a Stream that yields each
// parsed SSE chunk in turn. A non-2xx response is reported here as a typed *APIError
// before any iteration begins. The caller must Close the returned stream.
func (s *ChatCompletionsService) CreateStream(
	ctx context.Context,
	req ChatCompletionRequest,
) (*Stream, error) {
	req.stream = true
	return s.client.stream(ctx, "chat/completions", req)
}

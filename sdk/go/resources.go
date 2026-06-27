package auvex

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
)

// EmbeddingRequest is the request for an embeddings call. Input may be a single string
// or a slice of strings; any other OpenAI embeddings parameter goes in Extra.
type EmbeddingRequest struct {
	// Model is the embeddings model alias.
	Model string
	// Input is the text to embed — a string or a []string.
	Input any
	// Extra holds any other OpenAI embeddings parameter, merged into the request body.
	Extra map[string]any
}

// MarshalJSON serializes the request, merging Extra and then letting the typed fields win.
func (r EmbeddingRequest) MarshalJSON() ([]byte, error) {
	out := make(map[string]any, len(r.Extra)+2)
	for k, v := range r.Extra {
		out[k] = v
	}
	out["model"] = r.Model
	out["input"] = r.Input
	return json.Marshal(out)
}

// EmbeddingsService accesses POST /v1/embeddings.
type EmbeddingsService struct {
	client *Client
}

// Create returns embeddings for the given input. The gateway's response is returned
// verbatim as json.RawMessage.
func (s *EmbeddingsService) Create(
	ctx context.Context,
	req EmbeddingRequest,
) (json.RawMessage, error) {
	return s.client.request(ctx, http.MethodPost, "embeddings", req, nil)
}

// ImageRequest is the request for an image-generation call. Any other OpenAI image
// parameter (size, n, ...) goes in Extra.
type ImageRequest struct {
	// Model is the image model alias.
	Model string
	// Prompt is the (governed, redacted-before-egress) text prompt.
	Prompt string
	// Extra holds any other OpenAI image parameter, merged into the request body.
	Extra map[string]any
}

// MarshalJSON serializes the request, merging Extra and then letting the typed fields win.
func (r ImageRequest) MarshalJSON() ([]byte, error) {
	out := make(map[string]any, len(r.Extra)+2)
	for k, v := range r.Extra {
		out[k] = v
	}
	out["model"] = r.Model
	out["prompt"] = r.Prompt
	return json.Marshal(out)
}

// ImagesService accesses POST /v1/images/generations.
type ImagesService struct {
	client *Client
}

// Generate creates images from a text prompt. The gateway's response is returned
// verbatim as json.RawMessage.
func (s *ImagesService) Generate(
	ctx context.Context,
	req ImageRequest,
) (json.RawMessage, error) {
	return s.client.request(ctx, http.MethodPost, "images/generations", req, nil)
}

// ModerationResult is the native moderation result returned by Moderations.Create.
type ModerationResult struct {
	// Flagged is true when the input tripped any check.
	Flagged bool `json:"flagged"`
	// SensitiveData holds per-type counts of detected sensitive data, e.g.
	// {"email": 1, "credit_card": 1}.
	SensitiveData map[string]int `json:"sensitiveData"`
	// Injection lists the prompt-injection categories detected, if any.
	Injection []string `json:"injection"`
}

// ModerationsService accesses POST /v1/moderations.
type ModerationsService struct {
	client *Client
}

// Create screens text locally for sensitive data and prompt injection. This never calls
// a model provider — nothing leaves the gateway — so it is safe to use to pre-screen
// content before sending it anywhere.
func (s *ModerationsService) Create(ctx context.Context, input string) (*ModerationResult, error) {
	raw, err := s.client.request(
		ctx,
		http.MethodPost,
		"moderations",
		map[string]any{"input": input},
		nil,
	)
	if err != nil {
		return nil, err
	}
	var result ModerationResult
	if err := json.Unmarshal(raw, &result); err != nil {
		return nil, fmt.Errorf("auvex: decoding moderation response: %w", err)
	}
	return &result, nil
}

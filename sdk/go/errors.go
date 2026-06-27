package auvex

import (
	"errors"
	"fmt"
)

// Category sentinels for the gateway's error responses. Match against them with
// errors.Is, for example:
//
//	if errors.Is(err, auvex.ErrPolicyDenied) { ... }
//
// They classify a failure without forcing you to inspect status codes by hand. The
// transport sentinels (ErrTimeout, ErrConnection) cover failures where the request
// never produced an HTTP response.
var (
	// ErrBadRequest indicates a 400 — the request was malformed or failed validation.
	ErrBadRequest = errors.New("auvex: bad request")
	// ErrAuthentication indicates a 401 — the API key is missing, malformed, unknown,
	// revoked or expired.
	ErrAuthentication = errors.New("auvex: authentication failed")
	// ErrPolicyDenied indicates a 403 — the call was blocked by tenant policy or flagged
	// as a prompt injection.
	ErrPolicyDenied = errors.New("auvex: policy denied")
	// ErrNotFound indicates a 404 — the requested resource does not exist for this tenant.
	ErrNotFound = errors.New("auvex: not found")
	// ErrRateLimit indicates a 429 — the tenant's rate limit or call budget was exceeded.
	ErrRateLimit = errors.New("auvex: rate limited")
	// ErrUpstream indicates a 502/503/504 — the upstream model provider was unavailable
	// or timed out.
	ErrUpstream = errors.New("auvex: upstream error")
	// ErrTimeout indicates the request timed out before the gateway responded.
	ErrTimeout = errors.New("auvex: request timed out")
	// ErrConnection indicates the request never reached the gateway (DNS, refused
	// connection, TLS, etc.).
	ErrConnection = errors.New("auvex: connection error")
)

// APIError is the typed error returned for any non-2xx response from the gateway.
//
// The gateway emits an OpenAI-style error envelope:
//
//	{"error": {"message": "...", "type": "...", "code": "..."}}
//
// so the human-readable reason is always on Message, with the discriminators on Type
// and Code and the full decoded envelope on Body. Recover it with errors.As:
//
//	var apiErr *auvex.APIError
//	if errors.As(err, &apiErr) {
//		log.Printf("gateway said %d: %s", apiErr.StatusCode, apiErr.Message)
//	}
//
// It also classifies itself against a category sentinel, so errors.Is(err, ErrRateLimit)
// and friends work without unpacking the struct.
type APIError struct {
	// StatusCode is the HTTP status code of the response.
	StatusCode int
	// Message is the human-readable reason, taken from error.message when present.
	Message string
	// Type is the gateway's error.type discriminator, when present.
	Type string
	// Code is the gateway's error.code, when present (e.g. invalid_api_key).
	Code string
	// Body is the full decoded response body, when one was returned. It is the decoded
	// envelope for JSON responses, or the raw string for non-JSON (e.g. a plain-text 5xx).
	Body any

	// kind is the category sentinel this error matches under errors.Is.
	kind error
}

// Error returns a concise, human-readable description of the failure.
func (e *APIError) Error() string {
	if e.Code != "" {
		return fmt.Sprintf("auvex: HTTP %d (%s): %s", e.StatusCode, e.Code, e.Message)
	}
	return fmt.Sprintf("auvex: HTTP %d: %s", e.StatusCode, e.Message)
}

// Is reports whether this error belongs to the given category sentinel, letting
// errors.Is classify it by meaning rather than by status code.
func (e *APIError) Is(target error) bool {
	return e.kind != nil && target == e.kind
}

// kindForStatus maps an HTTP status code to its category sentinel, or nil if the status
// has no dedicated category. Checked before the error type.
func kindForStatus(status int) error {
	switch status {
	case 400:
		return ErrBadRequest
	case 401:
		return ErrAuthentication
	case 403:
		return ErrPolicyDenied
	case 404:
		return ErrNotFound
	case 429:
		return ErrRateLimit
	case 502, 503, 504:
		return ErrUpstream
	default:
		return nil
	}
}

// kindForType maps a gateway error.type value to its category sentinel, used as a
// fallback when the status code is not one of the dedicated ones.
func kindForType(errType string) error {
	switch errType {
	case "invalid_request_error":
		return ErrBadRequest
	case "policy_violation", "prompt_injection":
		return ErrPolicyDenied
	case "rate_limit_exceeded":
		return ErrRateLimit
	case "upstream_error":
		return ErrUpstream
	case "not_found":
		return ErrNotFound
	default:
		return nil
	}
}

// errorFromResponse builds the most specific *APIError for a non-2xx response.
//
// body is the decoded response body. It is expected to be the OpenAI-style envelope
// (a map[string]any), but any shape — including a plain string, for a non-JSON 5xx — is
// handled gracefully. The category is driven primarily by the status code, because a
// couple of distinct conditions share the same type (a 401 bad key and a 400 bad request
// both report invalid_request_error); the type is only a fallback for unmapped statuses.
func errorFromResponse(statusCode int, body any) *APIError {
	message := fmt.Sprintf("HTTP %d", statusCode)
	var errType, code string

	if m, ok := body.(map[string]any); ok {
		if envelope, ok := m["error"].(map[string]any); ok {
			if s, ok := envelope["message"].(string); ok && s != "" {
				message = s
			}
			if s, ok := envelope["type"].(string); ok {
				errType = s
			}
			if s, ok := envelope["code"].(string); ok {
				code = s
			}
		} else if s, ok := m["message"].(string); ok && s != "" {
			// Some proxies flatten the envelope; tolerate that too.
			message = s
		}
	}

	kind := kindForStatus(statusCode)
	if kind == nil && errType != "" {
		kind = kindForType(errType)
	}
	if kind == nil && statusCode >= 400 && statusCode < 500 {
		// An unknown 4xx is still a client error.
		kind = ErrBadRequest
	}

	return &APIError{
		StatusCode: statusCode,
		Message:    message,
		Type:       errType,
		Code:       code,
		Body:       body,
		kind:       kind,
	}
}

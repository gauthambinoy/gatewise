package gatewise

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"
	"time"
)

const (
	testBaseURL = "http://gateway.test"
	testAPIKey  = "gatewise_sk_test_123"
)

// captured records the details of the request the handler received so a test can assert
// on the exact verb, path, query, headers and body the client produced.
type captured struct {
	method string
	path   string
	query  url.Values
	header http.Header
	body   []byte
}

// recordHandler records the incoming request into cap and replies with the given status
// and JSON body.
func recordHandler(cap *captured, status int, respBody string) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		cap.method = r.Method
		cap.path = r.URL.Path
		cap.query = r.URL.Query()
		cap.header = r.Header.Clone()
		cap.body, _ = io.ReadAll(r.Body)
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(status)
		_, _ = io.WriteString(w, respBody)
	}
}

// newTestClient spins up an httptest server with the given handler and returns a client
// pointed at it, plus the server (which the test should Close).
func newTestClient(t *testing.T, handler http.Handler) (*Client, *httptest.Server) {
	t.Helper()
	srv := httptest.NewServer(handler)
	client, err := New(WithBaseURL(srv.URL), WithAPIKey(testAPIKey), WithHTTPClient(srv.Client()))
	if err != nil {
		srv.Close()
		t.Fatalf("New: %v", err)
	}
	return client, srv
}

// roundTripperFunc adapts a function to an http.RoundTripper for transport-level tests.
type roundTripperFunc func(*http.Request) (*http.Response, error)

func (f roundTripperFunc) RoundTrip(r *http.Request) (*http.Response, error) { return f(r) }

// -- configuration / env --------------------------------------------------------------

func TestEnvVarDefaults(t *testing.T) {
	t.Setenv("GATEWISE_BASE_URL", "http://env-host:9000")
	t.Setenv("GATEWISE_API_KEY", "gatewise_sk_env")
	client, err := New()
	if err != nil {
		t.Fatalf("New: %v", err)
	}
	if client.baseURL != "http://env-host:9000" {
		t.Errorf("baseURL = %q, want http://env-host:9000", client.baseURL)
	}
	if client.apiKey != "gatewise_sk_env" {
		t.Errorf("apiKey = %q, want gatewise_sk_env", client.apiKey)
	}
}

func TestExplicitOptionsBeatEnv(t *testing.T) {
	t.Setenv("GATEWISE_BASE_URL", "http://env-host:9000")
	t.Setenv("GATEWISE_API_KEY", "gatewise_sk_env")
	client, err := New(WithBaseURL("http://explicit:1234"), WithAPIKey("gatewise_sk_explicit"))
	if err != nil {
		t.Fatalf("New: %v", err)
	}
	if client.baseURL != "http://explicit:1234" {
		t.Errorf("baseURL = %q, want the explicit value", client.baseURL)
	}
	if client.apiKey != "gatewise_sk_explicit" {
		t.Errorf("apiKey = %q, want the explicit value", client.apiKey)
	}
}

func TestDefaultBaseURL(t *testing.T) {
	t.Setenv("GATEWISE_BASE_URL", "")
	client, err := New(WithAPIKey("gatewise_sk_x"))
	if err != nil {
		t.Fatalf("New: %v", err)
	}
	if client.baseURL != "http://localhost:8080" {
		t.Errorf("baseURL = %q, want the localhost default", client.baseURL)
	}
}

func TestMissingAPIKeyIsAnError(t *testing.T) {
	t.Setenv("GATEWISE_API_KEY", "")
	if _, err := New(WithBaseURL(testBaseURL)); err == nil {
		t.Fatal("expected an error when no API key is configured, got nil")
	}
}

func TestTrailingSlashStripped(t *testing.T) {
	client, err := New(WithBaseURL(testBaseURL+"/"), WithAPIKey(testAPIKey))
	if err != nil {
		t.Fatalf("New: %v", err)
	}
	if got := client.url("usage"); got != testBaseURL+"/v1/usage" {
		t.Errorf("url(usage) = %q, want %s/v1/usage", got, testBaseURL)
	}
}

// -- auth header + URL joining --------------------------------------------------------

func TestAuthHeaderAndContentTypeSent(t *testing.T) {
	var cap captured
	client, srv := newTestClient(t, recordHandler(&cap, 200, `{"choices":[]}`))
	defer srv.Close()

	_, err := client.Chat.Completions.Create(context.Background(), ChatCompletionRequest{
		Model:    "smart",
		Messages: []Message{{Role: "user", Content: "hi"}},
	})
	if err != nil {
		t.Fatalf("Create: %v", err)
	}
	if got := cap.header.Get("Authorization"); got != "Bearer "+testAPIKey {
		t.Errorf("Authorization = %q, want Bearer %s", got, testAPIKey)
	}
	if got := cap.header.Get("Content-Type"); got != "application/json" {
		t.Errorf("Content-Type = %q, want application/json", got)
	}
}

func TestBaseURLJoining(t *testing.T) {
	var cap captured
	client, srv := newTestClient(t, recordHandler(&cap, 200, `{"total":1}`))
	defer srv.Close()

	if _, err := client.Usage(context.Background()); err != nil {
		t.Fatalf("Usage: %v", err)
	}
	if cap.path != "/v1/usage" {
		t.Errorf("path = %q, want /v1/usage", cap.path)
	}
	if cap.method != http.MethodGet {
		t.Errorf("method = %q, want GET", cap.method)
	}
}

// -- each method hits the right path + verb -------------------------------------------

func TestChatCompletionsPathAndBody(t *testing.T) {
	reply := `{"id":"cmpl-1","choices":[{"message":{"content":"hi"}}]}`
	var cap captured
	client, srv := newTestClient(t, recordHandler(&cap, 200, reply))
	defer srv.Close()

	out, err := client.Chat.Completions.Create(context.Background(), ChatCompletionRequest{
		Model:    "smart",
		Messages: []Message{{Role: "user", Content: "hi"}},
		Extra:    map[string]any{"temperature": 0.2},
	})
	if err != nil {
		t.Fatalf("Create: %v", err)
	}
	if string(out) != reply {
		t.Errorf("response = %s, want %s", out, reply)
	}
	if cap.path != "/v1/chat/completions" || cap.method != http.MethodPost {
		t.Errorf("got %s %s, want POST /v1/chat/completions", cap.method, cap.path)
	}

	var body map[string]any
	if err := json.Unmarshal(cap.body, &body); err != nil {
		t.Fatalf("decode body: %v", err)
	}
	if body["model"] != "smart" {
		t.Errorf("model = %v, want smart", body["model"])
	}
	if body["temperature"] != 0.2 {
		t.Errorf("temperature = %v, want 0.2", body["temperature"])
	}
	// Non-streaming requests explicitly send stream:false.
	if body["stream"] != false {
		t.Errorf("stream = %v, want false", body["stream"])
	}
}

func TestEmbeddingsStringInput(t *testing.T) {
	var cap captured
	client, srv := newTestClient(t, recordHandler(&cap, 200, `{"data":[{"embedding":[0.1]}]}`))
	defer srv.Close()

	if _, err := client.Embeddings.Create(context.Background(), EmbeddingRequest{
		Model: "embed",
		Input: "hello",
	}); err != nil {
		t.Fatalf("Create: %v", err)
	}
	if cap.path != "/v1/embeddings" {
		t.Errorf("path = %q, want /v1/embeddings", cap.path)
	}
	var body map[string]any
	_ = json.Unmarshal(cap.body, &body)
	if body["input"] != "hello" {
		t.Errorf("input = %v, want hello", body["input"])
	}
}

func TestEmbeddingsListInput(t *testing.T) {
	var cap captured
	client, srv := newTestClient(t, recordHandler(&cap, 200, `{"data":[]}`))
	defer srv.Close()

	if _, err := client.Embeddings.Create(context.Background(), EmbeddingRequest{
		Model: "embed",
		Input: []string{"a", "b"},
	}); err != nil {
		t.Fatalf("Create: %v", err)
	}
	var body struct {
		Input []string `json:"input"`
	}
	_ = json.Unmarshal(cap.body, &body)
	if len(body.Input) != 2 || body.Input[0] != "a" || body.Input[1] != "b" {
		t.Errorf("input = %v, want [a b]", body.Input)
	}
}

func TestImagesGenerate(t *testing.T) {
	var cap captured
	client, srv := newTestClient(t, recordHandler(&cap, 200, `{"data":[{"url":"http://img"}]}`))
	defer srv.Close()

	out, err := client.Images.Generate(context.Background(), ImageRequest{
		Model:  "image",
		Prompt: "a cat",
	})
	if err != nil {
		t.Fatalf("Generate: %v", err)
	}
	if cap.path != "/v1/images/generations" {
		t.Errorf("path = %q, want /v1/images/generations", cap.path)
	}
	var parsed struct {
		Data []struct {
			URL string `json:"url"`
		} `json:"data"`
	}
	if err := json.Unmarshal(out, &parsed); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if parsed.Data[0].URL != "http://img" {
		t.Errorf("url = %q, want http://img", parsed.Data[0].URL)
	}
	var body map[string]any
	_ = json.Unmarshal(cap.body, &body)
	if body["prompt"] != "a cat" {
		t.Errorf("prompt = %v, want a cat", body["prompt"])
	}
}

func TestModerations(t *testing.T) {
	var cap captured
	client, srv := newTestClient(t, recordHandler(&cap, 200,
		`{"flagged":true,"sensitiveData":{"email":1},"injection":[]}`))
	defer srv.Close()

	out, err := client.Moderations.Create(context.Background(), "email jane@acme.com")
	if err != nil {
		t.Fatalf("Create: %v", err)
	}
	if !out.Flagged {
		t.Error("Flagged = false, want true")
	}
	if out.SensitiveData["email"] != 1 {
		t.Errorf("SensitiveData[email] = %d, want 1", out.SensitiveData["email"])
	}
	if cap.path != "/v1/moderations" {
		t.Errorf("path = %q, want /v1/moderations", cap.path)
	}
	var body map[string]any
	_ = json.Unmarshal(cap.body, &body)
	if body["input"] != "email jane@acme.com" {
		t.Errorf("input = %v, want the email string", body["input"])
	}
}

func TestUsageModelsPolicies(t *testing.T) {
	handler := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		switch r.URL.Path {
		case "/v1/usage":
			_, _ = io.WriteString(w, `{"total":5}`)
		case "/v1/models":
			_, _ = io.WriteString(w, `[{"alias":"smart","target":"gpt"}]`)
		case "/v1/policies":
			_, _ = io.WriteString(w, `[]`)
		default:
			w.WriteHeader(404)
		}
	})
	client, srv := newTestClient(t, handler)
	defer srv.Close()

	usage, err := client.Usage(context.Background())
	if err != nil {
		t.Fatalf("Usage: %v", err)
	}
	if string(usage) != `{"total":5}` {
		t.Errorf("usage = %s, want {\"total\":5}", usage)
	}

	models, err := client.Models(context.Background())
	if err != nil {
		t.Fatalf("Models: %v", err)
	}
	var list []map[string]any
	if err := json.Unmarshal(models, &list); err != nil {
		t.Fatalf("decode models: %v", err)
	}
	if len(list) != 1 || list[0]["alias"] != "smart" {
		t.Errorf("models = %s, want one entry aliased smart", models)
	}

	policies, err := client.Policies(context.Background())
	if err != nil {
		t.Fatalf("Policies: %v", err)
	}
	if string(policies) != `[]` {
		t.Errorf("policies = %s, want []", policies)
	}
}

func TestAuditQueryParams(t *testing.T) {
	var cap captured
	client, srv := newTestClient(t, recordHandler(&cap, 200, `{"entries":[],"total":0}`))
	defer srv.Close()

	if _, err := client.Audit(context.Background(), AuditParams{Q: "card", Verdict: "BLOCKED"}); err != nil {
		t.Fatalf("Audit: %v", err)
	}
	if cap.query.Get("q") != "card" {
		t.Errorf("q = %q, want card", cap.query.Get("q"))
	}
	if cap.query.Get("verdict") != "BLOCKED" {
		t.Errorf("verdict = %q, want BLOCKED", cap.query.Get("verdict"))
	}
}

func TestAuditOmitsEmptyParams(t *testing.T) {
	var cap captured
	client, srv := newTestClient(t, recordHandler(&cap, 200, `{"entries":[]}`))
	defer srv.Close()

	if _, err := client.Audit(context.Background(), AuditParams{}); err != nil {
		t.Fatalf("Audit: %v", err)
	}
	if cap.query.Has("q") {
		t.Error("q should be absent when not supplied")
	}
	if cap.query.Has("verdict") {
		t.Error("verdict should be absent when not supplied")
	}
}

// -- error envelope -> typed errors ---------------------------------------------------

func TestErrorEnvelopeMapsToSentinel(t *testing.T) {
	cases := []struct {
		status   int
		errType  string
		sentinel error
	}{
		{400, "invalid_request_error", ErrBadRequest},
		{401, "invalid_request_error", ErrAuthentication},
		{403, "policy_violation", ErrPolicyDenied},
		{403, "prompt_injection", ErrPolicyDenied},
		{404, "not_found", ErrNotFound},
		{429, "rate_limit_exceeded", ErrRateLimit},
		{502, "upstream_error", ErrUpstream},
		{503, "upstream_error", ErrUpstream},
		{504, "upstream_error", ErrUpstream},
	}

	for _, tc := range cases {
		tc := tc
		t.Run(http.StatusText(tc.status)+"_"+tc.errType, func(t *testing.T) {
			body := `{"error":{"message":"boom","type":"` + tc.errType + `","code":"x"}}`
			client, srv := newTestClient(t, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				w.Header().Set("Content-Type", "application/json")
				w.WriteHeader(tc.status)
				_, _ = io.WriteString(w, body)
			}))
			defer srv.Close()

			_, err := client.Chat.Completions.Create(context.Background(), ChatCompletionRequest{
				Model:    "smart",
				Messages: []Message{{Role: "user", Content: "x"}},
			})
			if err == nil {
				t.Fatal("expected an error, got nil")
			}
			if !errors.Is(err, tc.sentinel) {
				t.Errorf("errors.Is(err, sentinel) = false; err = %v", err)
			}
			var apiErr *APIError
			if !errors.As(err, &apiErr) {
				t.Fatalf("errors.As(*APIError) = false; err = %v", err)
			}
			if apiErr.StatusCode != tc.status {
				t.Errorf("StatusCode = %d, want %d", apiErr.StatusCode, tc.status)
			}
			if apiErr.Message != "boom" {
				t.Errorf("Message = %q, want boom", apiErr.Message)
			}
			if apiErr.Type != tc.errType {
				t.Errorf("Type = %q, want %q", apiErr.Type, tc.errType)
			}
		})
	}
}

func TestErrorFallsBackToTypeForUnmappedStatus(t *testing.T) {
	err := errorFromResponse(418, map[string]any{
		"error": map[string]any{"message": "teapot", "type": "rate_limit_exceeded"},
	})
	if !errors.Is(err, ErrRateLimit) {
		t.Errorf("errors.Is(err, ErrRateLimit) = false; err = %v", err)
	}
	if err.Message != "teapot" {
		t.Errorf("Message = %q, want teapot", err.Message)
	}
}

func TestErrorHandlesNonObjectBody(t *testing.T) {
	err := errorFromResponse(500, "gateway exploded")
	if err.StatusCode != 500 {
		t.Errorf("StatusCode = %d, want 500", err.StatusCode)
	}
	if !strings.Contains(err.Message, "HTTP 500") {
		t.Errorf("Message = %q, want it to mention HTTP 500", err.Message)
	}
}

// -- transport errors -----------------------------------------------------------------

func TestConnectionErrorMapping(t *testing.T) {
	hc := &http.Client{Transport: roundTripperFunc(func(*http.Request) (*http.Response, error) {
		return nil, errors.New("dial tcp: connection refused")
	})}
	client, err := New(WithBaseURL("http://unreachable.test"), WithAPIKey(testAPIKey), WithHTTPClient(hc))
	if err != nil {
		t.Fatalf("New: %v", err)
	}
	_, err = client.Usage(context.Background())
	if !errors.Is(err, ErrConnection) {
		t.Errorf("errors.Is(err, ErrConnection) = false; err = %v", err)
	}
}

func TestTimeoutErrorMapping(t *testing.T) {
	// The handler blocks until the request is cancelled, so the context deadline fires.
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		<-r.Context().Done()
	}))
	defer srv.Close()

	client, err := New(WithBaseURL(srv.URL), WithAPIKey(testAPIKey), WithHTTPClient(srv.Client()))
	if err != nil {
		t.Fatalf("New: %v", err)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Millisecond)
	defer cancel()
	_, err = client.Usage(ctx)
	if !errors.Is(err, ErrTimeout) {
		t.Errorf("errors.Is(err, ErrTimeout) = false; err = %v", err)
	}
}

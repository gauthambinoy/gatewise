package gatewise

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"os"
	"strings"
	"time"
)

const (
	defaultBaseURL = "http://localhost:8080"
	defaultTimeout = 60 * time.Second
	userAgent      = "gatewise-go/0.1.0"
)

// Client is a thin, typed client over the GateWise gateway's /v1 HTTP API.
//
// Create one with New. It is safe for concurrent use by multiple goroutines. The model
// and governance calls hang off the namespaced services (Chat, Embeddings, Images,
// Moderations) and the metadata calls (Usage, Audit, Models, Policies) are methods on
// the client itself.
type Client struct {
	baseURL    string
	apiKey     string
	httpClient *http.Client

	// Chat is the OpenAI-compatible chat endpoint, holding Completions.
	Chat *ChatService
	// Embeddings is the OpenAI-compatible embeddings endpoint.
	Embeddings *EmbeddingsService
	// Images is the OpenAI-compatible image-generation endpoint.
	Images *ImagesService
	// Moderations is GateWise's native, provider-free content screen.
	Moderations *ModerationsService
}

// config holds the resolved constructor options before a Client is built.
type config struct {
	baseURL    string
	apiKey     string
	timeout    time.Duration
	hasTimeout bool
	httpClient *http.Client
}

// Option configures a Client. Pass any number of these to New.
type Option func(*config)

// WithBaseURL sets the gateway host (the /v1 prefix is added for you). When omitted, the
// client falls back to the GATEWISE_BASE_URL environment variable, then to
// http://localhost:8080.
func WithBaseURL(baseURL string) Option {
	return func(c *config) { c.baseURL = baseURL }
}

// WithAPIKey sets the GateWise API key (gatewise_sk_...). When omitted, the client falls back
// to the GATEWISE_API_KEY environment variable.
func WithAPIKey(apiKey string) Option {
	return func(c *config) { c.apiKey = apiKey }
}

// WithTimeout sets the per-request timeout. It is ignored when WithHTTPClient is also
// supplied, since the caller's client then owns its own timeout. Defaults to 60s.
func WithTimeout(d time.Duration) Option {
	return func(c *config) {
		c.timeout = d
		c.hasTimeout = true
	}
}

// WithHTTPClient supplies an *http.Client to use as-is, for callers who need custom
// transport, proxy or TLS settings. When set, WithTimeout is ignored.
func WithHTTPClient(httpClient *http.Client) Option {
	return func(c *config) { c.httpClient = httpClient }
}

// New creates a Client from the given options.
//
// The base URL falls back to GATEWISE_BASE_URL (then to http://localhost:8080) and the API
// key falls back to GATEWISE_API_KEY. A missing API key is reported as an error here rather
// than surfacing later as a confusing 401.
func New(opts ...Option) (*Client, error) {
	cfg := &config{}
	for _, opt := range opts {
		opt(cfg)
	}

	baseURL := cfg.baseURL
	if baseURL == "" {
		baseURL = os.Getenv("GATEWISE_BASE_URL")
	}
	if baseURL == "" {
		baseURL = defaultBaseURL
	}
	baseURL = strings.TrimRight(baseURL, "/")

	apiKey := cfg.apiKey
	if apiKey == "" {
		apiKey = os.Getenv("GATEWISE_API_KEY")
	}
	if apiKey == "" {
		return nil, errors.New(
			"gatewise: an API key is required; pass gatewise.WithAPIKey(...) or set the GATEWISE_API_KEY environment variable",
		)
	}

	httpClient := cfg.httpClient
	if httpClient == nil {
		timeout := defaultTimeout
		if cfg.hasTimeout {
			timeout = cfg.timeout
		}
		httpClient = &http.Client{Timeout: timeout}
	}

	c := &Client{
		baseURL:    baseURL,
		apiKey:     apiKey,
		httpClient: httpClient,
	}
	c.Chat = &ChatService{Completions: &ChatCompletionsService{client: c}}
	c.Embeddings = &EmbeddingsService{client: c}
	c.Images = &ImagesService{client: c}
	c.Moderations = &ModerationsService{client: c}
	return c, nil
}

// url joins the gateway base URL with a /v1 API path.
func (c *Client) url(path string) string {
	return c.baseURL + "/v1/" + strings.TrimLeft(path, "/")
}

// doRequest builds and sends one request, returning the live response without reading
// or closing its body. Callers are responsible for closing resp.Body. Transport-level
// failures are mapped to the ErrTimeout / ErrConnection sentinels.
func (c *Client) doRequest(
	ctx context.Context,
	method, path string,
	body any,
	query url.Values,
) (*http.Response, error) {
	var reader io.Reader
	if body != nil {
		encoded, err := json.Marshal(body)
		if err != nil {
			return nil, fmt.Errorf("gatewise: encoding request body: %w", err)
		}
		reader = bytes.NewReader(encoded)
	}

	u := c.url(path)
	if len(query) > 0 {
		u += "?" + query.Encode()
	}

	req, err := http.NewRequestWithContext(ctx, method, u, reader)
	if err != nil {
		return nil, fmt.Errorf("gatewise: building request: %w", err)
	}
	req.Header.Set("Authorization", "Bearer "+c.apiKey)
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json")
	req.Header.Set("User-Agent", userAgent)

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, mapTransportError(err)
	}
	return resp, nil
}

// request sends a request and returns the decoded JSON body verbatim, raising a typed
// *APIError for any non-2xx status. The body is returned as json.RawMessage so callers
// can decode it into whatever shape they expect.
func (c *Client) request(
	ctx context.Context,
	method, path string,
	body any,
	query url.Values,
) (json.RawMessage, error) {
	resp, err := c.doRequest(ctx, method, path, body, query)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	raw, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, mapTransportError(err)
	}

	if resp.StatusCode >= 200 && resp.StatusCode < 300 {
		if len(raw) == 0 {
			return nil, nil
		}
		return json.RawMessage(raw), nil
	}
	return nil, errorFromResponse(resp.StatusCode, decodeBody(raw))
}

// -- governance + metadata endpoints --------------------------------------------------

// AuditParams holds the optional filters for an audit-log query. Empty fields are
// omitted from the request.
type AuditParams struct {
	// Q is a free-text filter over the redacted prompt, model and actor.
	Q string
	// Verdict filters to one verdict, e.g. ALLOWED, REDACTED or BLOCKED.
	Verdict string
}

// Usage returns the calling tenant's usage and cost summary (GET /v1/usage).
func (c *Client) Usage(ctx context.Context) (json.RawMessage, error) {
	return c.request(ctx, http.MethodGet, "usage", nil, nil)
}

// Audit queries the tenant's immutable, hash-chained audit log (GET /v1/audit). The
// result is a page of entries with paging metadata
// ({"entries": [...], "page", "size", "total"}).
func (c *Client) Audit(ctx context.Context, params AuditParams) (json.RawMessage, error) {
	query := url.Values{}
	if params.Q != "" {
		query.Set("q", params.Q)
	}
	if params.Verdict != "" {
		query.Set("verdict", params.Verdict)
	}
	return c.request(ctx, http.MethodGet, "audit", nil, query)
}

// Models lists the routing table / allowed model aliases (GET /v1/models). This doubles
// as the tenant's model allow-list.
func (c *Client) Models(ctx context.Context) (json.RawMessage, error) {
	return c.request(ctx, http.MethodGet, "models", nil, nil)
}

// Policies lists the tenant's allow / deny policy rules (GET /v1/policies).
func (c *Client) Policies(ctx context.Context) (json.RawMessage, error) {
	return c.request(ctx, http.MethodGet, "policies", nil, nil)
}

// -- shared helpers -------------------------------------------------------------------

// decodeBody decodes raw bytes as JSON, falling back to the raw string for non-JSON
// bodies and nil for an empty body. Used to populate APIError.Body.
func decodeBody(raw []byte) any {
	if len(raw) == 0 {
		return nil
	}
	var v any
	if err := json.Unmarshal(raw, &v); err != nil {
		return string(raw)
	}
	return v
}

// mapTransportError classifies a transport-level failure as a timeout or a generic
// connection error, wrapping the original so errors.Is and errors.Unwrap still work.
func mapTransportError(err error) error {
	if errors.Is(err, context.DeadlineExceeded) {
		return fmt.Errorf("%w: %v", ErrTimeout, err)
	}
	var netErr net.Error
	if errors.As(err, &netErr) && netErr.Timeout() {
		return fmt.Errorf("%w: %v", ErrTimeout, err)
	}
	return fmt.Errorf("%w: %v", ErrConnection, err)
}

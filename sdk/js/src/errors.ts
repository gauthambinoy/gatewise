/**
 * Typed error classes raised by the Auvex client.
 *
 * The gateway emits an OpenAI-style error envelope:
 *
 *     { "error": { "message": "...", "type": "...", "code": "..." } }
 *
 * so the human-readable reason is always on `AuvexError.message` and the raw envelope on
 * `AuvexError.body`.
 *
 * Mapping is driven primarily by the HTTP status code, because a couple of distinct
 * conditions share the same `type` (e.g. a 401 bad key and a 400 bad request both report
 * `invalid_request_error`). The `type` string is only a fallback when a status code does
 * not map to a more specific class.
 */

/** Shape of the gateway's OpenAI-style error envelope. */
export interface AuvexErrorBody {
  error?: {
    message?: string;
    type?: string;
    code?: string;
  };
  /** Some proxies flatten the envelope; tolerated on a best-effort basis. */
  message?: string;
  [key: string]: unknown;
}

/** Fields shared by every Auvex error. */
export interface AuvexErrorOptions {
  statusCode?: number;
  type?: string;
  code?: string;
  body?: unknown;
  cause?: unknown;
}

/** Base class for every error raised by the Auvex client. */
export class AuvexError extends Error {
  /** The HTTP status code, or `undefined` for transport-level failures. */
  readonly statusCode?: number;
  /** The gateway's `error.type` discriminator, when present. */
  readonly type?: string;
  /** The gateway's `error.code`, when present (e.g. `invalid_api_key`). */
  readonly code?: string;
  /** The full decoded response body, when one was returned. */
  readonly body?: unknown;

  constructor(message: string, options: AuvexErrorOptions = {}) {
    super(message, options.cause !== undefined ? { cause: options.cause } : undefined);
    this.name = new.target.name;
    this.statusCode = options.statusCode;
    this.type = options.type;
    this.code = options.code;
    this.body = options.body;
    // Keep `instanceof` working when this is compiled down for older runtimes.
    Object.setPrototypeOf(this, new.target.prototype);
  }
}

/** The request never reached the gateway (DNS, refused connection, TLS, etc.). */
export class APIConnectionError extends AuvexError {}

/** The request timed out before the gateway responded. */
export class APITimeoutError extends APIConnectionError {}

/** 400 — the request was malformed or failed validation. */
export class BadRequestError extends AuvexError {}

/** 401 — the API key is missing, malformed, unknown, revoked or expired. */
export class AuthenticationError extends AuvexError {}

/** 403 — the call was blocked by tenant policy or flagged as a prompt injection. */
export class PolicyDeniedError extends AuvexError {}

/** 404 — the requested resource does not exist for this tenant. */
export class NotFoundError extends AuvexError {}

/** 429 — the tenant's rate limit or call budget was exceeded. */
export class RateLimitError extends AuvexError {}

/** 502/503/504 — the upstream model provider was unavailable or timed out. */
export class UpstreamError extends AuvexError {}

type ErrorCtor = new (message: string, options?: AuvexErrorOptions) => AuvexError;

/** Status codes that map to a dedicated exception class. Checked before `type`. */
const STATUS_TO_ERROR: Record<number, ErrorCtor> = {
  400: BadRequestError,
  401: AuthenticationError,
  403: PolicyDeniedError,
  404: NotFoundError,
  429: RateLimitError,
  502: UpstreamError,
  503: UpstreamError,
  504: UpstreamError,
};

/** Gateway `error.type` values, used as a fallback when the status code is unmapped. */
const TYPE_TO_ERROR: Record<string, ErrorCtor> = {
  invalid_request_error: BadRequestError,
  policy_violation: PolicyDeniedError,
  prompt_injection: PolicyDeniedError,
  rate_limit_exceeded: RateLimitError,
  upstream_error: UpstreamError,
  not_found: NotFoundError,
};

/**
 * Build the most specific `AuvexError` for a non-2xx response.
 *
 * @param statusCode The HTTP status code of the response.
 * @param body The decoded response body. Expected to be the OpenAI-style envelope, but any
 *   shape (including a non-object, e.g. a plain-text 5xx) is handled gracefully.
 */
export function errorFromResponse(statusCode: number, body: unknown): AuvexError {
  let message = `HTTP ${statusCode}`;
  let type: string | undefined;
  let code: string | undefined;

  if (body && typeof body === 'object') {
    const envelope = (body as AuvexErrorBody).error;
    if (envelope && typeof envelope === 'object') {
      if (envelope.message) message = String(envelope.message);
      if (envelope.type != null) type = String(envelope.type);
      if (envelope.code != null) code = String(envelope.code);
    } else if (typeof (body as AuvexErrorBody).message === 'string') {
      message = String((body as AuvexErrorBody).message);
    }
  }

  let Ctor: ErrorCtor | undefined = STATUS_TO_ERROR[statusCode];
  if (!Ctor && type) Ctor = TYPE_TO_ERROR[type];
  if (!Ctor) {
    // Unknown 4xx -> generic client error; everything else -> base error.
    Ctor = statusCode >= 400 && statusCode < 500 ? BadRequestError : AuvexError;
  }

  return new Ctor(message, { statusCode, type, code, body });
}

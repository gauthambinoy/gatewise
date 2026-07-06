/**
 * Client for the GateWise AI governance gateway.
 *
 * GateWise is an OpenAI-compatible HTTP gateway. This client speaks its `/v1` API directly,
 * adds typed errors, and exposes the gateway's native governance endpoints (moderations,
 * usage, audit, models, policies) alongside the familiar chat / embeddings / images calls.
 *
 * Uses the global `fetch`, so it runs on Node 18+ and in the browser with no dependencies.
 *
 * If you already use the official `openai` package you do not need this client — just point
 * that one's `baseURL` and `apiKey` at your gateway. This exists for callers who want the
 * governance helpers and typed errors on their own.
 */

import {
  APIConnectionError,
  APITimeoutError,
  GateWiseError,
  errorFromResponse,
} from './errors.js';

/** A JSON-ish value. Provider responses are returned verbatim, so these stay loose. */
export type Json = unknown;

/** An OpenAI-style chat message. `content` is left open for multimodal payloads. */
export interface ChatMessage {
  role: 'system' | 'user' | 'assistant' | 'tool' | (string & {});
  content: unknown;
  name?: string;
  [key: string]: unknown;
}

/** Options accepted by the `GateWiseClient` constructor. */
export interface GateWiseClientOptions {
  /** Gateway host. Falls back to `GATEWISE_BASE_URL`, then `http://localhost:8080`. */
  baseUrl?: string;
  /** GateWise API key (`gatewise_sk_...`). Falls back to `GATEWISE_API_KEY`. */
  apiKey?: string;
  /** Per-request timeout in milliseconds. Defaults to 60000. */
  timeout?: number;
  /** Override the `fetch` implementation (advanced / testing). Defaults to global `fetch`. */
  fetch?: typeof fetch;
}

/** Base params shared by the non-streaming and streaming chat-completion overloads. */
export interface ChatCompletionParams {
  /** A model alias the gateway routes (e.g. `smart`, `fast`). */
  model: string;
  /** OpenAI-style message list. */
  messages: ChatMessage[];
  /** When `true`, `create` returns an async iterator of parsed SSE chunks. */
  stream?: boolean;
  /** Any other OpenAI chat parameter (temperature, max_tokens, user, tools, ...). */
  [key: string]: unknown;
}

/** Embeddings request params. `input` may be one string or an array of strings. */
export interface EmbeddingParams {
  model: string;
  input: string | string[];
  [key: string]: unknown;
}

/** Image-generation request params. */
export interface ImageParams {
  model: string;
  prompt: string;
  [key: string]: unknown;
}

/** Filters for an audit-log query. */
export interface AuditParams {
  /** Free-text filter over the redacted prompt, model and actor. */
  q?: string;
  /** Filter to one verdict, e.g. `ALLOWED`, `REDACTED`, `BLOCKED`. */
  verdict?: string;
}

/** The native moderation result returned by `client.moderations.create`. */
export interface ModerationResult {
  flagged: boolean;
  /** Per-type counts of detected sensitive data, e.g. `{ email: 1, credit_card: 1 }`. */
  sensitiveData: Record<string, number>;
  /** Injection categories detected, if any. */
  injection: string[];
}

const DEFAULT_BASE_URL = 'http://localhost:8080';
const DEFAULT_TIMEOUT = 60_000;

/** Read an environment variable in Node without assuming `process` exists (browser-safe). */
function readEnv(name: string): string | undefined {
  if (typeof process !== 'undefined' && process.env) {
    // A set-but-empty variable means "not configured" — fall through to the default.
    const value = process.env[name];
    return value === undefined || value === '' ? undefined : value;
  }
  return undefined;
}

/**
 * A thin, typed client over the GateWise gateway's `/v1` HTTP API.
 *
 * The `baseUrl` is the gateway **host** — the `/v1` prefix is added for you. It defaults to
 * `http://localhost:8080`.
 *
 * @example
 * ```ts
 * const client = new GateWiseClient({ apiKey: 'gatewise_sk_...' });
 * const reply = await client.chat.completions.create({
 *   model: 'smart',
 *   messages: [{ role: 'user', content: 'Hello' }],
 * });
 * ```
 */
export class GateWiseClient {
  readonly baseUrl: string;
  readonly apiKey: string;
  readonly timeout: number;

  private readonly fetchImpl: typeof fetch;

  /** The `chat` namespace, holding `completions`. */
  readonly chat: { completions: ChatCompletions };
  /** The OpenAI-compatible embeddings endpoint. */
  readonly embeddings: Embeddings;
  /** The OpenAI-compatible image-generation endpoint. */
  readonly images: Images;
  /** GateWise's native, provider-free content screen. */
  readonly moderations: Moderations;

  constructor(options: GateWiseClientOptions = {}) {
    const baseUrl = (options.baseUrl ?? readEnv('GATEWISE_BASE_URL') ?? DEFAULT_BASE_URL).replace(
      /\/+$/,
      '',
    );
    const apiKey = options.apiKey ?? readEnv('GATEWISE_API_KEY');
    if (!apiKey) {
      throw new Error(
        'An GateWise API key is required. Pass { apiKey } or set the GATEWISE_API_KEY environment variable.',
      );
    }

    const fetchImpl = options.fetch ?? globalThis.fetch;
    if (typeof fetchImpl !== 'function') {
      throw new Error(
        'No global fetch is available. Use Node 18+ or pass a fetch implementation via { fetch }.',
      );
    }

    this.baseUrl = baseUrl;
    this.apiKey = apiKey;
    this.timeout = options.timeout ?? DEFAULT_TIMEOUT;
    // Bind so the implementation can be a free function (e.g. a test stub).
    this.fetchImpl = fetchImpl;

    this.chat = { completions: new ChatCompletions(this) };
    this.embeddings = new Embeddings(this);
    this.images = new Images(this);
    this.moderations = new Moderations(this);
  }

  /** Join the gateway base URL with a `/v1` API path. */
  private url(path: string): string {
    return `${this.baseUrl}/v1/${path.replace(/^\/+/, '')}`;
  }

  private headers(): Record<string, string> {
    return {
      Authorization: `Bearer ${this.apiKey}`,
      'Content-Type': 'application/json',
      Accept: 'application/json',
      'User-Agent': 'gatewise-js/0.1.0',
    };
  }

  /** Run a fetch with a timeout via AbortController, mapping transport failures to typed errors. */
  private async doFetch(
    path: string,
    init: RequestInit & { query?: Record<string, string | undefined> },
  ): Promise<Response> {
    const { query, ...rest } = init;
    const url = new URL(this.url(path));
    if (query) {
      for (const [key, value] of Object.entries(query)) {
        if (value !== undefined && value !== null) url.searchParams.set(key, value);
      }
    }

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), this.timeout);
    try {
      return await this.fetchImpl(url.toString(), { ...rest, signal: controller.signal });
    } catch (err) {
      if (err instanceof Error && err.name === 'AbortError') {
        throw new APITimeoutError(`Request to GateWise timed out after ${this.timeout}ms`, {
          cause: err,
        });
      }
      throw new APIConnectionError(
        `Could not reach the GateWise gateway: ${(err as Error).message}`,
        { cause: err },
      );
    } finally {
      clearTimeout(timer);
    }
  }

  /** @internal Send a JSON request and return the decoded body, raising typed errors. */
  async request<T = Json>(
    method: string,
    path: string,
    opts: { body?: unknown; query?: Record<string, string | undefined> } = {},
  ): Promise<T> {
    const response = await this.doFetch(path, {
      method,
      headers: this.headers(),
      body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
      query: opts.query,
    });
    return (await this.handle(response)) as T;
  }

  /** Decode a response, raising a typed `GateWiseError` for any non-2xx status. */
  private async handle(response: Response): Promise<unknown> {
    const body = await readBody(response);
    if (response.ok) return body;
    throw errorFromResponse(response.status, body);
  }

  /**
   * @internal POST a streaming request and yield each parsed SSE `data:` chunk.
   *
   * SSE arrives as `data: {json}` lines; a final `data: [DONE]` sentinel ends the stream
   * and is not yielded. Empty or non-JSON `data:` payloads are skipped defensively.
   */
  async *stream(path: string, body: unknown): AsyncGenerator<Record<string, unknown>> {
    const response = await this.doFetch(path, {
      method: 'POST',
      headers: this.headers(),
      body: JSON.stringify(body),
    });

    if (!response.ok) {
      // Pull the (small) error body so we can raise a typed error.
      const errorBody = await readBody(response);
      throw errorFromResponse(response.status, errorBody);
    }
    if (!response.body) {
      throw new GateWiseError('The streaming response had no body.', {
        statusCode: response.status,
      });
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    try {
      for (;;) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });

        // Emit on event boundaries. SSE events are separated by a blank line; handle both
        // "\n\n" and "\r\n\r\n" so we don't depend on the server's newline style.
        let boundary = nextBoundary(buffer);
        while (boundary !== -1) {
          const rawEvent = buffer.slice(0, boundary.index);
          buffer = buffer.slice(boundary.index + boundary.length);
          const result = parseSseEvent(rawEvent);
          if (result === DONE) return;
          if (result !== undefined) yield result;
          boundary = nextBoundary(buffer);
        }
      }
      // Flush any trailing event the stream ended without a blank line after.
      const tail = parseSseEvent(buffer);
      if (tail !== undefined && tail !== DONE) yield tail;
    } finally {
      reader.releaseLock();
    }
  }

  // -- governance + metadata endpoints --------------------------------------------------

  /** Return the calling tenant's usage and cost summary (`GET /v1/usage`). */
  usage(): Promise<Json> {
    return this.request('GET', 'usage');
  }

  /**
   * Query the tenant's audit log (`GET /v1/audit`). Returns a page of entries with paging
   * metadata (`{ entries, page, size, total }`).
   */
  audit(params: AuditParams = {}): Promise<Json> {
    return this.request('GET', 'audit', { query: { q: params.q, verdict: params.verdict } });
  }

  /** List the routing table / allowed model aliases (`GET /v1/models`). */
  models(): Promise<Json> {
    return this.request('GET', 'models');
  }

  /** List the tenant's policy rules (`GET /v1/policies`). */
  policies(): Promise<Json> {
    return this.request('GET', 'policies');
  }
}

/** `client.chat.completions` — the OpenAI-compatible chat endpoint. */
export class ChatCompletions {
  constructor(private readonly client: GateWiseClient) {}

  /** Create a chat completion. Returns a parsed completion object. */
  create(params: ChatCompletionParams & { stream?: false }): Promise<Json>;
  /** Create a streaming chat completion. Returns an async iterator of parsed SSE chunks. */
  create(
    params: ChatCompletionParams & { stream: true },
  ): AsyncGenerator<Record<string, unknown>>;
  create(
    params: ChatCompletionParams,
  ): Promise<Json> | AsyncGenerator<Record<string, unknown>> {
    const { stream, ...rest } = params;
    if (stream) {
      return this.client.stream('chat/completions', { ...rest, stream: true });
    }
    return this.client.request('POST', 'chat/completions', { body: { ...rest, stream: false } });
  }
}

/** `client.embeddings` — the OpenAI-compatible embeddings endpoint. */
export class Embeddings {
  constructor(private readonly client: GateWiseClient) {}

  /** Create embeddings (`POST /v1/embeddings`). */
  create(params: EmbeddingParams): Promise<Json> {
    return this.client.request('POST', 'embeddings', { body: params });
  }
}

/** `client.images` — the OpenAI-compatible image-generation endpoint. */
export class Images {
  constructor(private readonly client: GateWiseClient) {}

  /** Generate images (`POST /v1/images/generations`). */
  generate(params: ImageParams): Promise<Json> {
    return this.client.request('POST', 'images/generations', { body: params });
  }
}

/** `client.moderations` — GateWise's native, provider-free content screen. */
export class Moderations {
  constructor(private readonly client: GateWiseClient) {}

  /**
   * Screen text locally for sensitive data and prompt injection (`POST /v1/moderations`).
   * This never calls a model provider — nothing leaves the gateway.
   */
  create(params: { input: string }): Promise<ModerationResult> {
    return this.client.request<ModerationResult>('POST', 'moderations', {
      body: { input: params.input },
    });
  }
}

// -- module-level helpers ---------------------------------------------------------------

/** Sentinel signalling the SSE stream's terminal `[DONE]` marker. */
const DONE = Symbol('gatewise.sse.done');

/** Read a response body as JSON, falling back to raw text (or null) for non-JSON bodies. */
async function readBody(response: Response): Promise<unknown> {
  const text = await response.text();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    // Non-JSON body (e.g. an upstream 5xx in plain text). Keep the raw text.
    return text;
  }
}

/** Find the next SSE event boundary (blank line) in the buffer, or `-1` if none yet. */
function nextBoundary(buffer: string): { index: number; length: number } | -1 {
  const lf = buffer.indexOf('\n\n');
  const crlf = buffer.indexOf('\r\n\r\n');
  if (crlf !== -1 && (lf === -1 || crlf < lf)) return { index: crlf, length: 4 };
  if (lf !== -1) return { index: lf, length: 2 };
  return -1;
}

/**
 * Parse one SSE event (which may span several `data:` lines) into a chunk object, the `DONE`
 * sentinel, or `undefined` to skip. Comment lines and non-`data:` fields are ignored.
 */
function parseSseEvent(rawEvent: string): Record<string, unknown> | typeof DONE | undefined {
  const dataParts: string[] = [];
  for (const line of rawEvent.split('\n')) {
    const trimmed = line.replace(/\r$/, '');
    if (!trimmed.startsWith('data:')) continue;
    dataParts.push(trimmed.slice('data:'.length).trim());
  }
  if (dataParts.length === 0) return undefined;
  const payload = dataParts.join('\n').trim();
  if (!payload) return undefined;
  if (payload === '[DONE]') return DONE;
  try {
    return JSON.parse(payload) as Record<string, unknown>;
  } catch {
    // A malformed chunk shouldn't kill the whole stream; skip it.
    return undefined;
  }
}

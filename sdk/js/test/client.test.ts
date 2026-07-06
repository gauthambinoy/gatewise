/**
 * Tests for the GateWise client. No network access — `fetch` is replaced with a stub on every
 * test, so each assertion can inspect the exact URL, verb, headers and body the client sent.
 */

import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  GateWiseClient,
  AuthenticationError,
  BadRequestError,
  NotFoundError,
  PolicyDeniedError,
  RateLimitError,
  UpstreamError,
  GateWiseError,
  errorFromResponse,
} from '../src/index.js';

const BASE_URL = 'http://gateway.test';
const API_KEY = 'gatewise_sk_test_123';

/** Build a JSON `Response` like the real fetch would return. */
function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

/** Build a fetch stub that records calls and returns a fixed (or computed) response. */
function stubFetch(
  responder: Response | ((url: string, init: RequestInit) => Response),
): { fetch: typeof fetch; calls: Array<{ url: string; init: RequestInit }> } {
  const calls: Array<{ url: string; init: RequestInit }> = [];
  const fetchImpl = vi.fn(async (url: string | URL | Request, init?: RequestInit) => {
    const u = String(url);
    const i = init ?? {};
    calls.push({ url: u, init: i });
    // Clone fixed responses so each call gets a readable body — a Response body is one-shot.
    return typeof responder === 'function' ? responder(u, i) : responder.clone();
  });
  return { fetch: fetchImpl as unknown as typeof fetch, calls };
}

function makeClient(responder: Response | ((url: string, init: RequestInit) => Response)) {
  const stub = stubFetch(responder);
  const client = new GateWiseClient({ baseUrl: BASE_URL, apiKey: API_KEY, fetch: stub.fetch });
  return { client, calls: stub.calls };
}

afterEach(() => {
  vi.restoreAllMocks();
});

// -- configuration / env ----------------------------------------------------------------

describe('configuration', () => {
  it('falls back to GATEWISE_* environment variables', () => {
    vi.stubEnv('GATEWISE_BASE_URL', 'http://env-host:9000');
    vi.stubEnv('GATEWISE_API_KEY', 'gatewise_sk_env');
    const client = new GateWiseClient({ fetch: stubFetch(jsonResponse({})).fetch });
    expect(client.baseUrl).toBe('http://env-host:9000');
    expect(client.apiKey).toBe('gatewise_sk_env');
    vi.unstubAllEnvs();
  });

  it('defaults the base URL to localhost:8080', () => {
    vi.stubEnv('GATEWISE_BASE_URL', '');
    const client = new GateWiseClient({ apiKey: 'k', fetch: stubFetch(jsonResponse({})).fetch });
    expect(client.baseUrl).toBe('http://localhost:8080');
    vi.unstubAllEnvs();
  });

  it('throws a clear error when no API key is configured', () => {
    vi.stubEnv('GATEWISE_API_KEY', '');
    expect(() => new GateWiseClient({ baseUrl: BASE_URL, fetch: stubFetch(jsonResponse({})).fetch }))
      .toThrow(/API key is required/);
    vi.unstubAllEnvs();
  });

  it('strips a trailing slash so the path is not doubled', async () => {
    const { client, calls } = (() => {
      const stub = stubFetch(jsonResponse({ total: 1 }));
      const c = new GateWiseClient({ baseUrl: BASE_URL + '/', apiKey: API_KEY, fetch: stub.fetch });
      return { client: c, calls: stub.calls };
    })();
    await client.usage();
    expect(calls[0]!.url).toBe(`${BASE_URL}/v1/usage`);
  });
});

// -- auth header + URL joining ----------------------------------------------------------

describe('request plumbing', () => {
  it('sends the Authorization: Bearer header on every request', async () => {
    const { client, calls } = makeClient(jsonResponse({ choices: [] }));
    await client.chat.completions.create({ model: 'smart', messages: [{ role: 'user', content: 'hi' }] });
    const headers = new Headers(calls[0]!.init.headers);
    expect(headers.get('Authorization')).toBe(`Bearer ${API_KEY}`);
    expect(headers.get('Content-Type')).toBe('application/json');
  });

  it('adds the /v1 prefix exactly once', async () => {
    const { client, calls } = makeClient(jsonResponse({ total: 1 }));
    await client.usage();
    expect(calls[0]!.url).toBe(`${BASE_URL}/v1/usage`);
    expect(calls[0]!.init.method).toBe('GET');
  });
});

// -- each method hits the right path + verb ---------------------------------------------

describe('methods', () => {
  it('chat.completions.create POSTs the right body', async () => {
    const reply = { id: 'cmpl-1', choices: [{ message: { content: 'hi' } }] };
    const { client, calls } = makeClient(jsonResponse(reply));
    const out = await client.chat.completions.create({
      model: 'smart',
      messages: [{ role: 'user', content: 'hi' }],
      temperature: 0.2,
    });
    expect(out).toEqual(reply);
    expect(calls[0]!.url).toBe(`${BASE_URL}/v1/chat/completions`);
    expect(calls[0]!.init.method).toBe('POST');
    const body = JSON.parse(calls[0]!.init.body as string);
    expect(body).toMatchObject({ model: 'smart', temperature: 0.2, stream: false });
  });

  it('embeddings.create accepts a string input', async () => {
    const { client, calls } = makeClient(jsonResponse({ data: [{ embedding: [0.1] }] }));
    await client.embeddings.create({ model: 'embed', input: 'hello' });
    expect(calls[0]!.url).toBe(`${BASE_URL}/v1/embeddings`);
    expect(JSON.parse(calls[0]!.init.body as string).input).toBe('hello');
  });

  it('embeddings.create accepts a list input', async () => {
    const { client, calls } = makeClient(jsonResponse({ data: [] }));
    await client.embeddings.create({ model: 'embed', input: ['a', 'b'] });
    expect(JSON.parse(calls[0]!.init.body as string).input).toEqual(['a', 'b']);
  });

  it('images.generate POSTs the prompt', async () => {
    const { client, calls } = makeClient(jsonResponse({ data: [{ url: 'http://img' }] }));
    const out = (await client.images.generate({ model: 'image', prompt: 'a cat' })) as {
      data: { url: string }[];
    };
    expect(out.data[0]!.url).toBe('http://img');
    expect(calls[0]!.url).toBe(`${BASE_URL}/v1/images/generations`);
    expect(JSON.parse(calls[0]!.init.body as string).prompt).toBe('a cat');
  });

  it('moderations.create POSTs input and returns the native result', async () => {
    const { client, calls } = makeClient(
      jsonResponse({ flagged: true, sensitiveData: { email: 1 }, injection: [] }),
    );
    const out = await client.moderations.create({ input: 'email jane@acme.com' });
    expect(out.flagged).toBe(true);
    expect(out.sensitiveData).toEqual({ email: 1 });
    expect(calls[0]!.url).toBe(`${BASE_URL}/v1/moderations`);
    expect(JSON.parse(calls[0]!.init.body as string)).toEqual({ input: 'email jane@acme.com' });
  });

  it('usage / models / policies GET the right paths', async () => {
    const { client, calls } = makeClient((url) => {
      if (url.endsWith('/usage')) return jsonResponse({ total: 5 });
      if (url.endsWith('/models')) return jsonResponse([{ alias: 'smart', target: 'gpt' }]);
      if (url.endsWith('/policies')) return jsonResponse([]);
      return jsonResponse({}, 404);
    });
    expect(await client.usage()).toEqual({ total: 5 });
    expect((await client.models()) as unknown[]).toHaveLength(1);
    expect(await client.policies()).toEqual([]);
    expect(calls.map((c) => c.init.method)).toEqual(['GET', 'GET', 'GET']);
  });

  it('audit serializes q/verdict and omits undefined params', async () => {
    const { client, calls } = makeClient(jsonResponse({ entries: [] }));
    await client.audit({ q: 'card', verdict: 'BLOCKED' });
    expect(calls[0]!.url).toBe(`${BASE_URL}/v1/audit?q=card&verdict=BLOCKED`);

    await client.audit();
    // No filters supplied -> no query string.
    expect(calls[1]!.url).toBe(`${BASE_URL}/v1/audit`);
  });
});

// -- error envelope -> typed exceptions -------------------------------------------------

describe('error mapping', () => {
  const cases: Array<[number, string, new (...a: never[]) => GateWiseError]> = [
    [400, 'invalid_request_error', BadRequestError],
    [401, 'invalid_request_error', AuthenticationError],
    [403, 'policy_violation', PolicyDeniedError],
    [403, 'prompt_injection', PolicyDeniedError],
    [404, 'not_found', NotFoundError],
    [429, 'rate_limit_exceeded', RateLimitError],
    [502, 'upstream_error', UpstreamError],
    [503, 'upstream_error', UpstreamError],
    [504, 'upstream_error', UpstreamError],
  ];

  for (const [status, type, Expected] of cases) {
    it(`maps HTTP ${status} (${type}) to ${Expected.name}`, async () => {
      const { client } = makeClient(
        jsonResponse({ error: { message: 'boom', type, code: 'x' } }, status),
      );
      await expect(
        client.chat.completions.create({ model: 'smart', messages: [{ role: 'user', content: 'x' }] }),
      ).rejects.toMatchObject({ message: 'boom', statusCode: status, type });
      // And it is an instance of the right class.
      const err = await client
        .chat.completions.create({ model: 'smart', messages: [{ role: 'user', content: 'x' }] })
        .catch((e) => e);
      expect(err).toBeInstanceOf(Expected);
      expect(err).toBeInstanceOf(GateWiseError);
    });
  }

  it('falls back to error.type when the status code is unmapped', () => {
    const err = errorFromResponse(418, { error: { message: 'teapot', type: 'rate_limit_exceeded' } });
    expect(err).toBeInstanceOf(RateLimitError);
    expect(err.message).toBe('teapot');
  });

  it('handles a non-object (plain text) error body', () => {
    const err = errorFromResponse(500, 'gateway exploded');
    expect(err.statusCode).toBe(500);
    expect(err.message).toContain('HTTP 500');
  });
});

// -- streaming --------------------------------------------------------------------------

/** Build a streaming Response whose body emits the given SSE text. */
function sseResponse(text: string, status = 200): Response {
  const stream = new ReadableStream<Uint8Array>({
    start(controller) {
      controller.enqueue(new TextEncoder().encode(text));
      controller.close();
    },
  });
  return new Response(stream, { status, headers: { 'Content-Type': 'text/event-stream' } });
}

describe('streaming', () => {
  it('yields each parsed SSE chunk and stops at [DONE]', async () => {
    const sse =
      'data: {"choices":[{"delta":{"content":"Hel"}}]}\n\n' +
      'data: {"choices":[{"delta":{"content":"lo"}}]}\n\n' +
      'data: [DONE]\n\n';
    const { client, calls } = makeClient(sseResponse(sse));

    const chunks: Record<string, unknown>[] = [];
    for await (const chunk of client.chat.completions.create({
      model: 'smart',
      messages: [{ role: 'user', content: 'hi' }],
      stream: true,
    })) {
      chunks.push(chunk);
    }

    expect(chunks).toHaveLength(2);
    expect((chunks[0]!.choices as any)[0].delta.content).toBe('Hel');
    expect((chunks[1]!.choices as any)[0].delta.content).toBe('lo');
    // The request advertised streaming to the gateway.
    expect(JSON.parse(calls[0]!.init.body as string).stream).toBe(true);
  });

  it('skips blank and malformed data lines without failing', async () => {
    const sse =
      '\n' +
      'data: \n\n' +
      'data: not-json\n\n' +
      'data: {"choices":[{"delta":{"content":"ok"}}]}\n\n' +
      'data: [DONE]\n\n';
    const { client } = makeClient(sseResponse(sse));

    const chunks: Record<string, unknown>[] = [];
    for await (const chunk of client.chat.completions.create({
      model: 'smart',
      messages: [{ role: 'user', content: 'hi' }],
      stream: true,
    })) {
      chunks.push(chunk);
    }
    expect(chunks).toHaveLength(1);
    expect((chunks[0]!.choices as any)[0].delta.content).toBe('ok');
  });

  it('raises a typed error on a non-2xx streaming response', async () => {
    const { client } = makeClient(
      jsonResponse({ error: { message: 'denied', type: 'policy_violation' } }, 403),
    );
    const iterate = async () => {
      for await (const _ of client.chat.completions.create({
        model: 'smart',
        messages: [{ role: 'user', content: 'hi' }],
        stream: true,
      })) {
        // no-op
      }
    };
    await expect(iterate()).rejects.toBeInstanceOf(PolicyDeniedError);
  });
});

/**
 * @gatewise/sdk — TypeScript/JavaScript client for the GateWise AI governance gateway.
 *
 * The gateway is OpenAI-compatible, so existing OpenAI code works by pointing it at your
 * gateway. This package adds typed errors and first-class access to GateWise's governance
 * endpoints (moderations, usage, audit, models, policies).
 *
 * @example
 * ```ts
 * import { GateWiseClient } from '@gatewise/sdk';
 *
 * const client = new GateWiseClient({ baseUrl: 'http://localhost:8080', apiKey: 'gatewise_sk_...' });
 * const reply = await client.chat.completions.create({
 *   model: 'smart',
 *   messages: [{ role: 'user', content: 'Hello' }],
 * });
 * ```
 */

export { GateWiseClient } from './client.js';
export type {
  GateWiseClientOptions,
  ChatCompletionParams,
  ChatMessage,
  EmbeddingParams,
  ImageParams,
  AuditParams,
  ModerationResult,
  Json,
} from './client.js';

export {
  GateWiseError,
  APIConnectionError,
  APITimeoutError,
  AuthenticationError,
  BadRequestError,
  NotFoundError,
  PolicyDeniedError,
  RateLimitError,
  UpstreamError,
  errorFromResponse,
} from './errors.js';
export type { GateWiseErrorBody, GateWiseErrorOptions } from './errors.js';

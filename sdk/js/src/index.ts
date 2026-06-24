/**
 * @auvex/sdk — TypeScript/JavaScript client for the Auvex AI governance gateway.
 *
 * The gateway is OpenAI-compatible, so existing OpenAI code works by pointing it at your
 * gateway. This package adds typed errors and first-class access to Auvex's governance
 * endpoints (moderations, usage, audit, models, policies).
 *
 * @example
 * ```ts
 * import { AuvexClient } from '@auvex/sdk';
 *
 * const client = new AuvexClient({ baseUrl: 'http://localhost:8080', apiKey: 'auvex_sk_...' });
 * const reply = await client.chat.completions.create({
 *   model: 'smart',
 *   messages: [{ role: 'user', content: 'Hello' }],
 * });
 * ```
 */

export { AuvexClient } from './client.js';
export type {
  AuvexClientOptions,
  ChatCompletionParams,
  ChatMessage,
  EmbeddingParams,
  ImageParams,
  AuditParams,
  ModerationResult,
  Json,
} from './client.js';

export {
  AuvexError,
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
export type { AuvexErrorBody, AuvexErrorOptions } from './errors.js';

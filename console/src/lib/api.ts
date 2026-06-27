// Typed gateway client. The API key is stored in the browser and sent as a Bearer
// token on every /v1 call; in dev Vite proxies these to the gateway (no CORS).

import type {
  ApiKey,
  AuditEntry,
  AuditPage,
  ChargebackReport,
  CreatedKey,
  Member,
  MemberInput,
  Policy,
  PolicyInput,
  Route,
  SsoProvider,
  ModerationResult,
  Tenant,
  UsageSummary,
  UserUsage,
  VerifyResult,
} from './types'

const BASE = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? ''
const KEY_STORAGE = 'auvex.apiKey'

export function getApiKey(): string | null {
  return localStorage.getItem(KEY_STORAGE)
}

export function setApiKey(key: string | null): void {
  if (key) localStorage.setItem(KEY_STORAGE, key)
  else localStorage.removeItem(KEY_STORAGE)
}

export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
    this.name = 'ApiError'
  }
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const key = getApiKey()
  const res = await fetch(BASE + path, {
    method,
    headers: {
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(key ? { Authorization: `Bearer ${key}` } : {}),
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })
  if (res.status === 204) return undefined as T
  const text = await res.text()
  const data = text ? JSON.parse(text) : undefined
  if (!res.ok) {
    const message = data?.error?.message ?? data?.message ?? `${res.status} ${res.statusText}`
    throw new ApiError(res.status, message)
  }
  return data as T
}

function query(params: Record<string, string | number | undefined>): string {
  const parts = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== '')
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
  return parts.length ? `?${parts.join('&')}` : ''
}

export interface AuditQuery {
  verdict?: string
  q?: string
  page?: number
  size?: number
}

export const api = {
  me: () => request<Tenant>('GET', '/v1/me'),

  usage: () => request<UsageSummary>('GET', '/v1/usage'),
  usageByUser: () => request<UserUsage[]>('GET', '/v1/usage/users'),
  chargeback: () => request<ChargebackReport>('GET', '/v1/usage/chargeback'),

  audit: (params: AuditQuery = {}) =>
    request<AuditPage>(
      'GET',
      '/v1/audit' +
        query({ verdict: params.verdict, q: params.q, page: params.page, size: params.size }),
    ),
  auditEntry: (id: string | number) => request<AuditEntry>('GET', `/v1/audit/${id}`),
  verify: () => request<VerifyResult>('GET', '/v1/audit/verify'),

  policies: () => request<Policy[]>('GET', '/v1/policies'),
  policy: (id: string) => request<Policy>('GET', `/v1/policies/${id}`),
  createPolicy: (p: PolicyInput) => request<Policy>('POST', '/v1/policies', p),
  updatePolicy: (id: string, p: PolicyInput) => request<Policy>('PUT', `/v1/policies/${id}`, p),
  deletePolicy: (id: string) => request<void>('DELETE', `/v1/policies/${id}`),

  members: () => request<Member[]>('GET', '/v1/members'),
  createMember: (m: MemberInput) => request<Member>('POST', '/v1/members', m),
  updateMember: (id: string, m: MemberInput) => request<Member>('PUT', `/v1/members/${id}`, m),
  deleteMember: (id: string) => request<void>('DELETE', `/v1/members/${id}`),

  models: () => request<Route[]>('GET', '/v1/models'),

  // Native moderation — used by the Connect page's live "Test connection" (no LLM key needed).
  moderate: (input: string) =>
    request<ModerationResult>('POST', '/v1/moderations', { input }),

  keys: () => request<ApiKey[]>('GET', '/v1/keys'),
  createKey: (name?: string) => request<CreatedKey>('POST', '/v1/keys', { name }),
  revokeKey: (id: string) => request<void>('DELETE', `/v1/keys/${id}`),

  providers: () => request<SsoProvider[]>('GET', '/auth/providers'),
}

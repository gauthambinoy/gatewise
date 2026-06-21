// API response shapes — mirror the gateway's JSON (see docs/ARCHITECTURE.md).

export interface Tenant {
  id: string
  name: string
  slug: string
}

export interface UsageSummary {
  totalCalls: number
  allowed: number
  blocked: number
  redacted: number
  errored: number
  byModel: Record<string, number>
  totalCostUsd: number
  totalTokens: number
  redactionByType: Record<string, number>
}

export interface UserUsage {
  actor: string
  requests: number
  redacted: number
  blocked: number
  costUsd: number
}

export interface AuditEntry {
  id: number
  requestId: string
  actor: string
  model: string
  verdict: string
  promptRedacted: string
  prevHash: string | null
  entryHash: string
  createdAt: string
  promptTokens: number | null
  completionTokens: number | null
  costUsd: number | null
  redactionCounts: Record<string, number> | null
}

export interface AuditPage {
  entries: AuditEntry[]
  page: number
  size: number
  total: number
}

export interface VerifyResult {
  intact: boolean
  firstBrokenId: number | null
}

export interface Policy {
  id: string
  name: string
  effect: 'allow' | 'deny' | 'redact'
  resourceType: 'model' | 'data_type' | 'user'
  resourceValue: string
  priority: number
  enabled: boolean
}

export interface PolicyInput {
  name: string
  effect: string
  resourceType: string
  resourceValue: string
  priority?: number
  enabled?: boolean
}

export interface Member {
  id: string
  email: string
  name: string | null
  role: 'owner' | 'security_admin' | 'auditor'
  status: string
  createdAt: string
}

export interface MemberInput {
  email: string
  name?: string
  role: string
  status?: string
}

export interface Route {
  alias: string
  target: string
}

export interface ApiKey {
  id: string
  name: string
  prefix: string
  status: string
  createdAt: string
  lastUsedAt: string | null
}

export interface CreatedKey extends ApiKey {
  secret: string
}

export interface SsoProvider {
  name: string
  configured: boolean
}

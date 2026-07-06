-- GateWise core schema (V1).
--
-- Four tables the gateway is built around: tenants, the API keys that resolve to
-- them, their allow/deny policies, and the immutable audit log. Everything is
-- scoped to a tenant so one customer's data can never bleed into another's.

-- A customer organisation. The root of multi-tenant isolation.
CREATE TABLE tenant (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT         NOT NULL,
    slug        TEXT         NOT NULL UNIQUE,
    status      TEXT         NOT NULL DEFAULT 'active',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT tenant_status_chk CHECK (status IN ('active', 'suspended'))
);

-- Credentials callers present. Each key resolves to exactly one tenant. We store
-- only a hash of the secret (never the raw key) plus a short prefix for display.
CREATE TABLE api_key (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    name          TEXT         NOT NULL,
    key_hash      TEXT         NOT NULL UNIQUE,
    prefix        TEXT         NOT NULL,
    status        TEXT         NOT NULL DEFAULT 'active',
    last_used_at  TIMESTAMPTZ,
    expires_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT api_key_status_chk CHECK (status IN ('active', 'revoked'))
);
CREATE INDEX api_key_tenant_idx ON api_key (tenant_id);

-- Per-tenant allow/deny rules. Higher priority wins; deny breaks ties (the
-- precedence logic itself lands with the policy engine in a later slice).
CREATE TABLE policy (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    name            TEXT         NOT NULL,
    effect          TEXT         NOT NULL,
    resource_type   TEXT         NOT NULL,
    resource_value  TEXT         NOT NULL,
    priority        INTEGER      NOT NULL DEFAULT 100,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT policy_effect_chk CHECK (effect IN ('allow', 'deny')),
    CONSTRAINT policy_resource_type_chk
        CHECK (resource_type IN ('model', 'data_type', 'user'))
);
CREATE INDEX policy_tenant_idx ON policy (tenant_id);

-- Append-only, hash-chained audit of every gateway decision. Each row carries the
-- previous row's hash, so tampering breaks the chain (verification lands in 2.5).
-- Only redacted text is stored here — raw PII must never reach this table.
CREATE TABLE audit_log (
    id                 BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          UUID         NOT NULL REFERENCES tenant (id),
    request_id         UUID         NOT NULL,
    actor              TEXT,
    model              TEXT,
    verdict            TEXT         NOT NULL,
    prompt_redacted    TEXT,
    response_redacted  TEXT,
    prev_hash          TEXT,
    entry_hash         TEXT         NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT audit_log_verdict_chk
        CHECK (verdict IN ('allowed', 'blocked', 'redacted', 'error'))
);
CREATE INDEX audit_log_tenant_created_idx ON audit_log (tenant_id, created_at);
CREATE UNIQUE INDEX audit_log_entry_hash_idx ON audit_log (entry_hash);

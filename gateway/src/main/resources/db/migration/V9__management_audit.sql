-- Attribution log for console administrative changes (API keys, members, policies): who did what,
-- when. Kept separate from audit_log (the hash-chained record of governed AI calls): these are
-- management events, not model traffic, and don't participate in the tamper-evident chain.
CREATE TABLE management_audit (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenant (id),
    principal_type  TEXT,
    principal_id    UUID,
    principal_email TEXT,
    action          TEXT NOT NULL,
    resource_type   TEXT NOT NULL,
    resource_id     UUID,
    detail          TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- The console lists a tenant's recent actions newest-first; index for exactly that access path.
CREATE INDEX management_audit_tenant_idx ON management_audit (tenant_id, id DESC);

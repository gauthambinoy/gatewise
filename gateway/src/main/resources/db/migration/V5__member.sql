-- Console members and their roles (the Team & roles page). A member is a human who can sign in to
-- the console; distinct from an api_key, which authenticates a machine. Roles gate console actions.
CREATE TABLE member (
    id         UUID PRIMARY KEY,
    tenant_id  UUID NOT NULL REFERENCES tenant (id),
    email      TEXT NOT NULL,
    name       TEXT,
    role       TEXT NOT NULL,                 -- 'owner' | 'security_admin' | 'auditor'
    status     TEXT NOT NULL DEFAULT 'invited', -- 'invited' | 'active'
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, email)
);

CREATE INDEX member_tenant_id_idx ON member (tenant_id);

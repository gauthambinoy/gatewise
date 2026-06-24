-- Human-in-the-loop approval queue. When a high-risk call matches a review trigger, it is held here
-- as 'pending' instead of being forwarded; a reviewer approves or rejects it, and an approved prompt
-- is then allowed through. Distinct from the audit log, which records what actually happened.
CREATE TABLE held_request (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenant (id),
    actor           TEXT NOT NULL,
    model           TEXT NOT NULL,
    prompt_hash     TEXT NOT NULL,                  -- ties an approval to a specific (redacted) prompt
    prompt_redacted TEXT,
    reason          TEXT,                           -- why it was held (e.g. 'sensitive data: credit_card')
    status          TEXT NOT NULL DEFAULT 'pending', -- 'pending' | 'approved' | 'rejected'
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_at      TIMESTAMPTZ,
    decided_by      TEXT
);

CREATE INDEX held_request_tenant_status_idx ON held_request (tenant_id, status);
CREATE INDEX held_request_hash_idx ON held_request (tenant_id, prompt_hash, status);

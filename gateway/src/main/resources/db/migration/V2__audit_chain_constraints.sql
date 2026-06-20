-- Hardening for the append-only audit chain (added with the audit feature in 2.5).

-- Anti-fork guarantee: within one tenant's chain, each prev_hash links to exactly one successor.
-- Combined with the per-tenant advisory lock taken on append, this makes it impossible for two
-- concurrent writes to both extend the same chain head (the second hits this constraint).
ALTER TABLE audit_log
    ADD CONSTRAINT audit_log_chain_link_uq UNIQUE (tenant_id, prev_hash);

-- The append path reads the current chain head (the highest id for the tenant); this index keeps
-- that lookup O(log n).
CREATE INDEX audit_log_tenant_id_desc_idx ON audit_log (tenant_id, id DESC);

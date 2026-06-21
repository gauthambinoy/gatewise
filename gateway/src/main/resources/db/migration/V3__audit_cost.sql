-- Cost & token accounting on the audit log (added with the cost feature).
-- Nullable: streaming responses and blocked/over-budget requests have no token usage.
ALTER TABLE audit_log
    ADD COLUMN prompt_tokens     INTEGER,
    ADD COLUMN completion_tokens INTEGER,
    ADD COLUMN cost_usd          NUMERIC(14, 6);

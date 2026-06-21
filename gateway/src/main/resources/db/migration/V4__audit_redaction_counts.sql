-- Per-type redaction counts for the "leaks prevented" analytics (added with that feature).
-- Analytics metadata (not part of the tamper-evident hash chain); jsonb so it aggregates in SQL.
ALTER TABLE audit_log
    ADD COLUMN redaction_counts JSONB;

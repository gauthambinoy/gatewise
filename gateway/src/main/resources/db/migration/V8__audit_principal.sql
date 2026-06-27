-- Records which principal performed each audited call: an API key for programmatic traffic, or a
-- console member for human-initiated actions. These are reference copies, not foreign keys: the
-- audit log is immutable and must survive the later deletion of the key or member it names. They sit
-- outside the hash chain (like redaction_counts), so backfilling them onto existing rows as NULL
-- never invalidates an existing chain.
ALTER TABLE audit_log ADD COLUMN principal_type  TEXT;
ALTER TABLE audit_log ADD COLUMN principal_id    UUID;
ALTER TABLE audit_log ADD COLUMN principal_email TEXT;

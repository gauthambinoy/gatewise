-- Legal hold: entries flagged here are preserved during litigation or investigation and are exempt
-- from retention deletion, even once they fall outside the retention window. Outside the hash chain
-- (like the principal columns), so toggling it never invalidates an existing chain.
ALTER TABLE audit_log ADD COLUMN legal_hold BOOLEAN NOT NULL DEFAULT FALSE;

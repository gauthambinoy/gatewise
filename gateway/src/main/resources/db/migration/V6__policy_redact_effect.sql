-- The 'redact' policy effect was added after V1; widen the effect CHECK to permit it
-- so a redact rule can be persisted (the engine already understands it).
ALTER TABLE policy DROP CONSTRAINT policy_effect_chk;
ALTER TABLE policy ADD CONSTRAINT policy_effect_chk CHECK (effect IN ('allow', 'deny', 'redact'));

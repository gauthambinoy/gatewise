-- Data residency: the region a tenant's data is pinned to (e.g. 'eu-west-1'). When residency is
-- enabled (gatewise.residency.enabled), a pinned tenant may only use models that run in this region
-- (gatewise.residency.model-regions maps a provider model → region). NULL = unrestricted, so existing
-- tenants are unaffected.
ALTER TABLE tenant ADD COLUMN residency_region TEXT;

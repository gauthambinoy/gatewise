package com.auvex.gateway.auth;

import java.util.UUID;

/** The principal bound to a request once its API key has been verified. */
public record AuthenticatedTenant(UUID tenantId, UUID apiKeyId) {}

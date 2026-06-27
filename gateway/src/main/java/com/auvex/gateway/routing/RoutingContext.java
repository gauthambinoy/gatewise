package com.auvex.gateway.routing;

import java.util.UUID;

/**
 * The information a {@link RoutingStrategy} may use to choose among candidate models.
 *
 * @param tenantId the calling tenant
 * @param estimatedPromptTokens a rough size of the request, for cost or latency estimates
 */
public record RoutingContext(UUID tenantId, int estimatedPromptTokens) {}

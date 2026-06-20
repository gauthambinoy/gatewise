package com.auvex.gateway.policy;

import java.util.Set;
import java.util.UUID;

/**
 * The request being judged.
 *
 * @param tenantId the tenant making the call
 * @param requestedModel the resolved provider model
 * @param actor the end-user id within the tenant (or a placeholder when unknown)
 * @param detectedDataTypes the sensitive data types found in the prompt (e.g. {@code credit_card})
 */
public record EvaluationContext(
    UUID tenantId, String requestedModel, String actor, Set<String> detectedDataTypes) {

  public EvaluationContext {
    detectedDataTypes = Set.copyOf(detectedDataTypes);
  }
}

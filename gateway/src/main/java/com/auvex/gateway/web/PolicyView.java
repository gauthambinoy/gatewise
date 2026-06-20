package com.auvex.gateway.web;

import com.auvex.gateway.policy.Policy;
import java.util.UUID;

/** The public representation of a policy rule returned by the API. */
public record PolicyView(
    UUID id,
    String name,
    String effect,
    String resourceType,
    String resourceValue,
    int priority,
    boolean enabled) {

  /** Projects a stored policy into its API view. */
  public static PolicyView of(Policy policy) {
    return new PolicyView(
        policy.getId(),
        policy.getName(),
        policy.getEffect(),
        policy.getResourceType(),
        policy.getResourceValue(),
        policy.getPriority(),
        policy.isEnabled());
  }
}

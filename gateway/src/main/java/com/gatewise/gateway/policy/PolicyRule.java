package com.gatewise.gateway.policy;

import java.util.UUID;

/** An immutable projection of one {@code policy} row, as the engine evaluates it. */
public record PolicyRule(
    UUID id,
    UUID tenantId,
    String name,
    Effect effect,
    ResourceType resourceType,
    String resourceValue,
    int priority,
    boolean enabled) {

  /** A resourceValue of "*" matches anything of its resource type. */
  public static final String WILDCARD = "*";
}

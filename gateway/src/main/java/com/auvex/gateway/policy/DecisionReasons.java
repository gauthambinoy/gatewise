package com.auvex.gateway.policy;

/** Builds the human-readable reason string attached to a decision. */
public final class DecisionReasons {

  private DecisionReasons() {}

  /** Describes which rule decided the outcome and why. */
  public static String describe(PolicyRule winner) {
    return "%s by rule '%s' (%s %s %s, priority %d)."
        .formatted(
            winner.effect() == Effect.DENY ? "Denied" : "Allowed",
            winner.name(),
            winner.effect(),
            winner.resourceType(),
            winner.resourceValue(),
            winner.priority());
  }
}

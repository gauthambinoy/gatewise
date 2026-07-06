package com.gatewise.gateway.policy;

/** Decides whether a single rule applies to a request. */
public final class RuleMatcher {

  private RuleMatcher() {}

  /** True when the rule is in scope for the tenant, enabled, and applies to the context. */
  public static boolean matches(PolicyRule rule, EvaluationContext ctx) {
    if (!rule.tenantId().equals(ctx.tenantId())) {
      return false; // defence in depth; the repository should already scope by tenant
    }
    if (!rule.enabled()) {
      return false;
    }
    return switch (rule.resourceType()) {
      case MODEL -> valueMatches(rule.resourceValue(), ctx.requestedModel());
      case USER -> valueMatches(rule.resourceValue(), ctx.actor());
      case DATA_TYPE ->
          ctx.detectedDataTypes().stream()
              .anyMatch(dataType -> valueMatches(rule.resourceValue(), dataType));
      case UNKNOWN -> false; // a bad row never matches; it can't crash or silently allow
    };
  }

  // Exact match, or the wildcard "*" which matches anything of this resource type.
  private static boolean valueMatches(String ruleValue, String contextValue) {
    return PolicyRule.WILDCARD.equals(ruleValue) || ruleValue.equals(contextValue);
  }
}

package com.auvex.gateway.policy;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Loads a tenant's stored rules and evaluates a request against them.
 *
 * <p>Reconciles the engine's strict default-deny with Auvex's drop-in promise: a tenant with no
 * enabled rules is treated as <em>ungoverned</em> and allowed, so a fresh tenant works immediately.
 * The moment a tenant adds any rule it gets strict evaluation (the engine's default-deny applies
 * among its rules). A fully fail-closed deployment would instead seed an explicit {@code ALLOW
 * model *} on tenant creation and drop this shortcut.
 */
@Component
public class PolicyEnforcement {

  private final PolicyRepository policies;
  private final PolicyEngine engine;

  public PolicyEnforcement(PolicyRepository policies, PolicyEngine engine) {
    this.policies = policies;
    this.engine = engine;
  }

  /** Returns the decision for a request; ungoverned tenants (no enabled rules) are allowed. */
  public Decision evaluate(EvaluationContext ctx) {
    List<PolicyRule> rules =
        policies.findByTenantIdAndEnabledTrue(ctx.tenantId()).stream()
            .map(PolicyEnforcement::toRule)
            .toList();
    if (rules.isEmpty()) {
      return new Decision(true, List.of(), "Tenant has no active policies; allowed.");
    }
    return engine.evaluate(ctx, rules);
  }

  // Map a stored row to the engine's immutable view, parsing the enum text defensively.
  private static PolicyRule toRule(Policy policy) {
    return new PolicyRule(
        policy.getId(),
        policy.getTenantId(),
        policy.getName(),
        Effect.from(policy.getEffect()),
        ResourceType.from(policy.getResourceType()),
        policy.getResourceValue(),
        policy.getPriority(),
        policy.isEnabled());
  }
}

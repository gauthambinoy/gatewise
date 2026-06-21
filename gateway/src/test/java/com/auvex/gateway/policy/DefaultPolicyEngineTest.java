package com.auvex.gateway.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Covers the policy engine's allow/deny, default-deny, precedence and edge-case behaviour. */
class DefaultPolicyEngineTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final String MODEL = "openai/gpt-4o";

  private final PolicyEngine engine = new DefaultPolicyEngine();

  private static PolicyRule rule(Effect effect, ResourceType type, String value, int priority) {
    return new PolicyRule(UUID.randomUUID(), TENANT, "r", effect, type, value, priority, true);
  }

  private static EvaluationContext ctx(Set<String> dataTypes) {
    return new EvaluationContext(TENANT, MODEL, "u1", dataTypes);
  }

  @Test
  void noPoliciesDefaultsToDeny() { // V1 / T24
    Decision d = engine.evaluate(ctx(Set.of()), List.of());
    assertThat(d.allowed()).isFalse();
    assertThat(d.matchedRules()).isEmpty();
    assertThat(d.reason()).containsIgnoringCase("default");
  }

  @Test
  void cleanAllow() { // V2 / T22
    Decision d =
        engine.evaluate(ctx(Set.of()), List.of(rule(Effect.ALLOW, ResourceType.MODEL, MODEL, 100)));
    assertThat(d.allowed()).isTrue();
  }

  @Test
  void denyBeatsAllowOnPriorityTie() { // V3 / T25
    PolicyRule allow = rule(Effect.ALLOW, ResourceType.MODEL, MODEL, 100);
    PolicyRule deny = rule(Effect.DENY, ResourceType.MODEL, MODEL, 100);
    Decision d = engine.evaluate(ctx(Set.of()), List.of(allow, deny));
    assertThat(d.allowed()).isFalse();
    assertThat(d.matchedRules()).first().isEqualTo(deny);
  }

  @Test
  void higherPriorityAllowOverridesDeny() { // V4 / T25
    Decision d =
        engine.evaluate(
            ctx(Set.of()),
            List.of(
                rule(Effect.DENY, ResourceType.MODEL, MODEL, 100),
                rule(Effect.ALLOW, ResourceType.MODEL, MODEL, 200)));
    assertThat(d.allowed()).isTrue();
  }

  @Test
  void wildcardAllowsAnyModel() { // V5
    Decision d =
        engine.evaluate(ctx(Set.of()), List.of(rule(Effect.ALLOW, ResourceType.MODEL, "*", 1)));
    assertThat(d.allowed()).isTrue();
  }

  @Test
  void higherPriorityDenyBeatsWildcardAllow() { // V6 / T23
    Decision d =
        engine.evaluate(
            ctx(Set.of("email")),
            List.of(
                rule(Effect.ALLOW, ResourceType.MODEL, "*", 1),
                rule(Effect.DENY, ResourceType.DATA_TYPE, "email", 100)));
    assertThat(d.allowed()).isFalse();
  }

  @Test
  void dataTypeRuleMatchesAnyDetectedType() { // V7
    Decision d =
        engine.evaluate(
            ctx(Set.of("email", "credit_card")),
            List.of(
                rule(Effect.ALLOW, ResourceType.MODEL, "*", 1),
                rule(Effect.DENY, ResourceType.DATA_TYPE, "credit_card", 50)));
    assertThat(d.allowed()).isFalse();
  }

  @Test
  void disabledRuleIsIgnored() { // V8
    PolicyRule disabledDeny =
        new PolicyRule(
            UUID.randomUUID(), TENANT, "r", Effect.DENY, ResourceType.MODEL, MODEL, 500, false);
    Decision d =
        engine.evaluate(
            ctx(Set.of()),
            List.of(disabledDeny, rule(Effect.ALLOW, ResourceType.MODEL, MODEL, 100)));
    assertThat(d.allowed()).isTrue();
  }

  @Test
  void bannedUserIsDenied() { // V9
    Decision d =
        engine.evaluate(
            ctx(Set.of()),
            List.of(
                rule(Effect.ALLOW, ResourceType.MODEL, "*", 1),
                rule(Effect.DENY, ResourceType.USER, "u1", 100)));
    assertThat(d.allowed()).isFalse();
  }

  @Test
  void unknownResourceTypeNeverMatches() { // V10
    PolicyRule unknown =
        new PolicyRule(
            UUID.randomUUID(), TENANT, "r", Effect.DENY, ResourceType.UNKNOWN, "xyz", 999, true);
    Decision d =
        engine.evaluate(
            ctx(Set.of()), List.of(unknown, rule(Effect.ALLOW, ResourceType.MODEL, MODEL, 100)));
    assertThat(d.allowed()).isTrue();
  }

  @Test
  void redactRuleAllows() { // V12 — REDACT permits (masking is always applied)
    Decision d =
        engine.evaluate(
            ctx(Set.of("email")),
            List.of(rule(Effect.REDACT, ResourceType.DATA_TYPE, "email", 100)));
    assertThat(d.allowed()).isTrue();
    assertThat(d.matchedRules()).first().extracting(PolicyRule::effect).isEqualTo(Effect.REDACT);
  }

  @Test
  void redactBeatsAllowButStillAllows() { // V13 — REDACT is more restrictive than ALLOW
    PolicyRule allow = rule(Effect.ALLOW, ResourceType.MODEL, MODEL, 100);
    PolicyRule redact = rule(Effect.REDACT, ResourceType.MODEL, MODEL, 100);
    Decision d = engine.evaluate(ctx(Set.of()), List.of(allow, redact));
    assertThat(d.allowed()).isTrue();
    assertThat(d.matchedRules()).first().isEqualTo(redact);
  }

  @Test
  void denyBeatsRedactOnPriorityTie() { // V14 — DENY > REDACT
    PolicyRule redact = rule(Effect.REDACT, ResourceType.MODEL, MODEL, 100);
    PolicyRule deny = rule(Effect.DENY, ResourceType.MODEL, MODEL, 100);
    Decision d = engine.evaluate(ctx(Set.of()), List.of(redact, deny));
    assertThat(d.allowed()).isFalse();
    assertThat(d.matchedRules()).first().isEqualTo(deny);
  }

  @Test
  void rulesFromAnotherTenantAreIgnored() { // V11
    PolicyRule otherTenant =
        new PolicyRule(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "r",
            Effect.ALLOW,
            ResourceType.MODEL,
            MODEL,
            100,
            true);
    Decision d = engine.evaluate(ctx(Set.of()), List.of(otherTenant));
    assertThat(d.allowed()).isFalse();
  }
}

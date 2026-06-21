package com.auvex.gateway.policy;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The policy engine: filters to matching rules, picks one winner by the precedence order, and
 * defaults to DENY when nothing matches.
 *
 * <p>Default-deny ("deny-unknown") is deliberate — for a security gateway, wrongly allowing (data
 * exfiltration, a compliance breach) is far worse than wrongly blocking (a fixable config gap). A
 * tenant opts into allow-by-default with one explicit {@code ALLOW model *} rule.
 */
@Component
public class DefaultPolicyEngine implements PolicyEngine {

  private static final String DEFAULT_DENY_REASON =
      "No policy matched; denied by default (deny-unknown).";

  @Override
  public Decision evaluate(EvaluationContext ctx, List<PolicyRule> rules) {
    List<PolicyRule> matching =
        rules == null
            ? List.of()
            : rules.stream().filter(rule -> RuleMatcher.matches(rule, ctx)).toList();

    if (matching.isEmpty()) {
      return new Decision(false, List.of(), DEFAULT_DENY_REASON);
    }

    PolicyRule winner = matching.stream().min(RulePrecedence.WINNER).orElseThrow();
    // ALLOW and REDACT both permit (masking is always applied); only DENY blocks.
    return new Decision(
        winner.effect() != Effect.DENY, List.of(winner), DecisionReasons.describe(winner));
  }
}

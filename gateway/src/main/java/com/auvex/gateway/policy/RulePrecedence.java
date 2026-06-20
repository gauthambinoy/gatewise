package com.auvex.gateway.policy;

import java.util.Comparator;

/**
 * The total order that picks one winner among matching rules.
 *
 * <p>Higher priority wins; on a tie DENY beats ALLOW (most-restrictive); a final id tiebreak keeps
 * the cited winner stable without ever changing the outcome. The winner is the {@code min} under
 * this comparator.
 */
public final class RulePrecedence {

  private RulePrecedence() {}

  public static final Comparator<PolicyRule> WINNER =
      Comparator.comparingInt(PolicyRule::priority)
          .reversed()
          .thenComparingInt(rule -> rule.effect() == Effect.DENY ? 0 : 1)
          .thenComparing(PolicyRule::id);
}

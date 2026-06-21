package com.auvex.gateway.policy;

import java.util.Comparator;

/**
 * The total order that picks one winner among matching rules.
 *
 * <p>Higher priority wins; on a tie the most-restrictive effect wins (DENY &gt; REDACT &gt; ALLOW);
 * a final id tiebreak keeps the cited winner stable without ever changing the outcome. The winner
 * is the {@code min} under this comparator.
 */
public final class RulePrecedence {

  private RulePrecedence() {}

  public static final Comparator<PolicyRule> WINNER =
      Comparator.comparingInt(PolicyRule::priority)
          .reversed()
          .thenComparingInt(RulePrecedence::restrictiveness)
          .thenComparing(PolicyRule::id);

  // Lower is more restrictive, so it wins the min().
  private static int restrictiveness(PolicyRule rule) {
    return switch (rule.effect()) {
      case DENY -> 0;
      case REDACT -> 1;
      case ALLOW -> 2;
    };
  }
}

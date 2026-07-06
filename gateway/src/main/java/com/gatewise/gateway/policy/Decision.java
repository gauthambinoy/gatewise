package com.gatewise.gateway.policy;

import java.util.List;

/**
 * The outcome of evaluating a request against a tenant's policies.
 *
 * @param allowed whether the request may proceed
 * @param matchedRules the winning rule (index 0); empty only when the default was applied
 * @param reason a human-readable explanation, safe to log or return
 */
public record Decision(boolean allowed, List<PolicyRule> matchedRules, String reason) {

  public Decision {
    matchedRules = List.copyOf(matchedRules);
  }
}

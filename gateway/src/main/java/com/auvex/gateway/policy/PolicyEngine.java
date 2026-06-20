package com.auvex.gateway.policy;

import java.util.List;

/** Evaluates a request against a tenant's policy rules and returns an allow/deny decision. */
public interface PolicyEngine {

  /**
   * Evaluates {@code ctx} against {@code rules}.
   *
   * @param ctx the request to judge
   * @param rules the candidate rules (out-of-tenant and disabled rows are filtered out here); an
   *     empty or null list yields the safe default
   * @return a non-null Decision — "no match" is the default path, never an error
   */
  Decision evaluate(EvaluationContext ctx, List<PolicyRule> rules);
}

package com.auvex.gateway.injection;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Detects attempts to strip the model's guardrails, e.g. "you have no restrictions", "without any
 * filters", "ignore your safety guidelines". Keyed on a "you ... no" / "ignore your" context so
 * benign sentences like "there are no restrictions on parking" do not match.
 */
@Component
public class RestrictionBypassRule extends RegexInjectionRule {

  private static final Pattern PATTERN =
      Pattern.compile(
          "(?:you\\s+(?:are|have|now have)\\s+no|without\\s+any|no\\s+longer\\s+have)\\s+"
              + "(?:restrictions|filters|rules|guidelines|limitations|censorship|safety)"
              + "|ignore\\s+your\\s+(?:safety\\s+)?(?:guidelines|policies|rules)",
          Pattern.CASE_INSENSITIVE);

  public RestrictionBypassRule() {
    super("RestrictionBypassRule", "restriction_bypass", PATTERN);
  }
}

package com.gatewise.gateway.injection;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Detects well-known jailbreak personas and triggers, e.g. "DAN" / "do anything now", "developer
 * mode enabled", "jailbreak", "STAN", "AIM".
 */
@Component
public class JailbreakPersonaRule extends RegexInjectionRule {

  private static final Pattern PATTERN =
      Pattern.compile(
          "\\b(?:DAN|STAN|AIM)\\b"
              + "|do\\s+anything\\s+now"
              + "|developer\\s+mode\\s+(?:enabled|on)"
              + "|jailbreak",
          Pattern.CASE_INSENSITIVE);

  public JailbreakPersonaRule() {
    super("JailbreakPersonaRule", "jailbreak", PATTERN);
  }
}

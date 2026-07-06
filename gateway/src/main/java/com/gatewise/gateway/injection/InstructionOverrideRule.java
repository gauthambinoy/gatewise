package com.gatewise.gateway.injection;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Detects attempts to override the system, e.g. "ignore all previous instructions", "disregard the
 * above rules", "forget everything you were told".
 */
@Component
public class InstructionOverrideRule extends RegexInjectionRule {

  private static final Pattern PATTERN =
      Pattern.compile(
          "(?:ignore|disregard|forget|override)\\s+"
              + "(?:all\\s+|any\\s+|the\\s+|your\\s+|everything\\s+)?"
              + "(?:previous|prior|above|earlier|preceding)?\\s*"
              + "(?:instructions|rules|prompts?|context|guidelines"
              + "|you\\s+were\\s+told|above)",
          Pattern.CASE_INSENSITIVE);

  public InstructionOverrideRule() {
    super("InstructionOverrideRule", "instruction_override", PATTERN);
  }
}

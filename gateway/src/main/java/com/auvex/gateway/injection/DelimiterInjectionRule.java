package com.auvex.gateway.injection;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Detects fake role/system markers injected into user text, e.g. {@code <|im_start|>system}, {@code
 * ### system:}, {@code [SYSTEM]}, {@code <<SYS>>}.
 */
@Component
public class DelimiterInjectionRule extends RegexInjectionRule {

  private static final Pattern PATTERN =
      Pattern.compile(
          "(?:<\\|im_start\\|>\\s*system"
              + "|#{2,}\\s*system\\s*:"
              + "|\\[\\s*system\\s*\\]"
              + "|<<\\s*sys\\s*>>)",
          Pattern.CASE_INSENSITIVE);

  public DelimiterInjectionRule() {
    super("DelimiterInjectionRule", "delimiter_injection", PATTERN);
  }
}

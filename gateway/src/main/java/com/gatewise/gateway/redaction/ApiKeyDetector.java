package com.gatewise.gateway.redaction;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Detects generic API keys / bearer tokens.
 *
 * <p>Keyword-anchored to control false positives, and masks only the captured value (group 1) so
 * the label stays — e.g. {@code Authorization: Bearer <token>} keeps the word but hides the token.
 */
@Component
public class ApiKeyDetector extends RegexDetector {

  private static final Pattern API_KEY =
      Pattern.compile(
          "(?i)\\b(?:api[_-]?key|token|secret|bearer|authorization)\\b"
              + "[\"'\\s:=]{1,4}([A-Za-z0-9._\\-]{16,512})");

  public ApiKeyDetector() {
    super(PiiType.API_KEY, 5, API_KEY);
  }

  @Override
  protected int maskGroup() {
    return 1;
  }
}

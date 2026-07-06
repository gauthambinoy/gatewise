package com.gatewise.gateway.redaction;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Masks a passport number, anchored on the word "passport" so a short alphanumeric code is redacted
 * only when it's clearly a passport, not any 6–9 character token.
 */
@Component
public class PassportDetector extends RegexDetector {

  private static final Pattern PATTERN =
      Pattern.compile("(?i:passport)\\s*(?i:no\\.?|number|#)?\\s*[:\\-]?\\s+([A-Z0-9]{6,9})\\b");

  public PassportDetector() {
    super(PiiType.PASSPORT, 7, PATTERN);
  }

  @Override
  protected int maskGroup() {
    return 1;
  }
}

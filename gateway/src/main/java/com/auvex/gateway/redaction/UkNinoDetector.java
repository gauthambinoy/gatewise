package com.auvex.gateway.redaction;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Detects UK National Insurance numbers (two valid prefix letters, six digits, suffix A-D). */
@Component
public class UkNinoDetector extends RegexDetector {

  private static final Pattern NINO =
      Pattern.compile("\\b[A-CEGHJ-PR-TW-Z][A-CEGHJ-NPR-TW-Z]\\d{6}[A-D]\\b");

  public UkNinoDetector() {
    super(PiiType.UK_NINO, 8, NINO);
  }
}

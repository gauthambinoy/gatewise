package com.auvex.gateway.redaction;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Detects Indian Permanent Account Numbers (five letters, four digits, one letter). */
@Component
public class IndiaPanDetector extends RegexDetector {

  private static final Pattern PAN = Pattern.compile("\\b[A-Z]{5}\\d{4}[A-Z]\\b");

  public IndiaPanDetector() {
    super(PiiType.INDIA_PAN, 8, PAN);
  }
}

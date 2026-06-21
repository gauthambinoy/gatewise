package com.auvex.gateway.redaction;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Detects US/international phone numbers (low priority so cards/SSNs win any overlap). */
@Component
public class PhoneDetector extends RegexDetector {

  private static final Pattern PHONE =
      Pattern.compile(
          "(?<!\\d)\\+?\\d{1,3}[\\s.\\-]?\\(?\\d{3}\\)?[\\s.\\-]?\\d{3}[\\s.\\-]?\\d{4}(?!\\d)");

  public PhoneDetector() {
    super(PiiType.PHONE, 5, PHONE);
  }
}

package com.gatewise.gateway.redaction;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Detects email addresses (pragmatic, not full RFC-5322 — over-matching a non-secret is safe). */
@Component
public class EmailDetector extends RegexDetector {

  private static final Pattern EMAIL =
      Pattern.compile("\\b[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,24}\\b");

  public EmailDetector() {
    super(PiiType.EMAIL, 4, EMAIL);
  }
}

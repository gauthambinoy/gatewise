package com.auvex.gateway.redaction;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Detects JSON Web Tokens (three base64url segments; the {@code eyJ} prefixes are the signal). */
@Component
public class JwtDetector extends RegexDetector {

  private static final Pattern JWT =
      Pattern.compile("\\beyJ[A-Za-z0-9_-]{5,}\\.eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\b");

  public JwtDetector() {
    super(PiiType.JWT, 1, JWT);
  }
}

package com.auvex.gateway.redaction;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Detects Google API keys (the {@code AIza} prefix followed by 35 url-safe characters). */
@Component
public class GoogleApiKeyDetector extends RegexDetector {

  private static final Pattern GOOGLE_API_KEY = Pattern.compile("\\bAIza[0-9A-Za-z_-]{35}\\b");

  public GoogleApiKeyDetector() {
    super(PiiType.GOOGLE_API_KEY, 9, GOOGLE_API_KEY);
  }
}

package com.gatewise.gateway.policy;

import java.util.Locale;

/**
 * What a matched policy rule does. Redaction is always applied for safety, so both {@code ALLOW}
 * and {@code REDACT} permit the request; {@code REDACT} states the masking requirement explicitly
 * (the recommended effect for external models). {@code DENY} blocks it.
 */
public enum Effect {
  ALLOW,
  REDACT,
  DENY;

  /** Parses the stored text; anything unrecognised is treated as DENY (fail-safe). */
  public static Effect from(String raw) {
    if (raw == null) {
      return DENY;
    }
    return switch (raw.toLowerCase(Locale.ROOT)) {
      case "allow" -> ALLOW;
      case "redact" -> REDACT;
      default -> DENY;
    };
  }
}

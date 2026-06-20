package com.auvex.gateway.policy;

import java.util.Locale;

/** Whether a matched policy rule permits or blocks the request. */
public enum Effect {
  ALLOW,
  DENY;

  /** Parses the stored text; anything unrecognised is treated as DENY (fail-safe). */
  public static Effect from(String raw) {
    if (raw != null && "allow".equals(raw.toLowerCase(Locale.ROOT))) {
      return ALLOW;
    }
    return DENY;
  }
}

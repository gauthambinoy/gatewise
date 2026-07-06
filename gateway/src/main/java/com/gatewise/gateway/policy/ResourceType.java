package com.gatewise.gateway.policy;

import java.util.Locale;

/** What a policy rule governs. An unrecognised stored value becomes UNKNOWN and never matches. */
public enum ResourceType {
  MODEL,
  DATA_TYPE,
  USER,
  UNKNOWN;

  /**
   * Parses the stored text; an unrecognised value is UNKNOWN so a bad row can't crash evaluation.
   */
  public static ResourceType from(String raw) {
    if (raw == null) {
      return UNKNOWN;
    }
    return switch (raw.toLowerCase(Locale.ROOT)) {
      case "model" -> MODEL;
      case "data_type" -> DATA_TYPE;
      case "user" -> USER;
      default -> UNKNOWN;
    };
  }
}

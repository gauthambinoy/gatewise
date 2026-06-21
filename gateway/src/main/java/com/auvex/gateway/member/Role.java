package com.auvex.gateway.member;

import java.util.Locale;

/**
 * A console member's role.
 *
 * <p>{@code OWNER} manages everything including billing and members; {@code SECURITY_ADMIN} manages
 * policy, routing and keys; {@code AUDITOR} has read-only access to logs and usage.
 */
public enum Role {
  OWNER,
  SECURITY_ADMIN,
  AUDITOR;

  /** The stored lowercase form (e.g. {@code security_admin}). */
  public String value() {
    return name().toLowerCase(Locale.ROOT);
  }

  /** Parses the stored text; an unknown value is the least-privileged role (fail-safe). */
  public static Role from(String raw) {
    if (raw != null) {
      try {
        return valueOf(raw.toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ignored) {
        // fall through to the safe default
      }
    }
    return AUDITOR;
  }
}

package com.gatewise.gateway.moderation;

import java.util.Locale;

/**
 * A content-safety category, aligned with the well-known moderation taxonomy (hate, harassment,
 * self-harm, sexual, violence). The JSON form uses a hyphen, e.g. {@code self-harm}.
 */
public enum ModerationCategory {
  HATE,
  HARASSMENT,
  SELF_HARM,
  SEXUAL,
  VIOLENCE;

  /** The wire form, e.g. {@code self-harm}. */
  public String value() {
    return name().toLowerCase(Locale.ROOT).replace('_', '-');
  }
}

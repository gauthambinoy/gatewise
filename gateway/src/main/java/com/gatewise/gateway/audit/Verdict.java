package com.gatewise.gateway.audit;

import java.util.Locale;

/** The gateway's decision about a call, as recorded in the audit log. */
public enum Verdict {
  ALLOWED,
  BLOCKED,
  REDACTED,
  ERROR;

  /** The lowercase form stored in the database. */
  public String value() {
    return name().toLowerCase(Locale.ROOT);
  }

  /** Parses the stored value back into a Verdict. */
  public static Verdict from(String raw) {
    return valueOf(raw.toUpperCase(Locale.ROOT));
  }
}

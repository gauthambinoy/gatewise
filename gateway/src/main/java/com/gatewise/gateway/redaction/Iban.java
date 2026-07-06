package com.gatewise.gateway.redaction;

import java.util.Locale;

/** The IBAN mod-97 checksum, used to confirm an IBAN-shaped string is actually valid. */
public final class Iban {

  private Iban() {}

  /** True if the candidate (spaces allowed) is a checksum-valid IBAN. */
  public static boolean isValid(String raw) {
    String s = raw.replace(" ", "").toUpperCase(Locale.ROOT);
    if (s.length() < 15 || s.length() > 34) {
      return false;
    }
    if (!s.matches("[A-Z]{2}\\d{2}[A-Z0-9]+")) {
      return false;
    }
    // Move the first four chars to the end, expand letters (A=10..Z=35), then take mod 97
    // digit-by-digit so we never need BigInteger.
    String rearranged = s.substring(4) + s.substring(0, 4);
    int mod = 0;
    for (int i = 0; i < rearranged.length(); i++) {
      char c = rearranged.charAt(i);
      int value = (c >= 'A') ? (c - 'A' + 10) : (c - '0');
      mod = (value >= 10) ? (mod * 100 + value) % 97 : (mod * 10 + value) % 97;
    }
    return mod == 1;
  }
}

package com.auvex.gateway.redaction;

/** The Luhn checksum, used to weed out random digit runs that aren't real card numbers. */
public final class Luhn {

  private Luhn() {}

  /** True if the all-digit string passes the Luhn check (and is at least two digits). */
  public static boolean isValid(String digits) {
    int sum = 0;
    boolean doubleIt = false;
    for (int i = digits.length() - 1; i >= 0; i--) {
      char c = digits.charAt(i);
      if (c < '0' || c > '9') {
        return false;
      }
      int d = c - '0';
      if (doubleIt) {
        d <<= 1;
        if (d > 9) {
          d -= 9;
        }
      }
      sum += d;
      doubleIt = !doubleIt;
    }
    return digits.length() >= 2 && sum % 10 == 0;
  }
}

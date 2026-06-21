package com.auvex.gateway.redaction;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Detects Indian Aadhaar numbers (12 digits, first digit 2-9), validated with the Verhoeff checksum
 * so look-alike numbers aren't masked.
 */
@Component
public class IndiaAadhaarDetector extends RegexDetector {

  private static final Pattern AADHAAR = Pattern.compile("\\b[2-9]\\d{3}\\s?\\d{4}\\s?\\d{4}\\b");

  /** Verhoeff multiplication table (d8). */
  private static final int[][] D = {
    {0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
    {1, 2, 3, 4, 0, 6, 7, 8, 9, 5},
    {2, 3, 4, 0, 1, 7, 8, 9, 5, 6},
    {3, 4, 0, 1, 2, 8, 9, 5, 6, 7},
    {4, 0, 1, 2, 3, 9, 5, 6, 7, 8},
    {5, 9, 8, 7, 6, 0, 4, 3, 2, 1},
    {6, 5, 9, 8, 7, 1, 0, 4, 3, 2},
    {7, 6, 5, 9, 8, 2, 1, 0, 4, 3},
    {8, 7, 6, 5, 9, 3, 2, 1, 0, 4},
    {9, 8, 7, 6, 5, 4, 3, 2, 1, 0}
  };

  /** Verhoeff permutation table. */
  private static final int[][] P = {
    {0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
    {1, 5, 7, 6, 2, 8, 3, 0, 9, 4},
    {5, 8, 0, 3, 7, 9, 6, 1, 4, 2},
    {8, 9, 1, 6, 0, 4, 3, 5, 2, 7},
    {9, 4, 5, 3, 1, 2, 6, 8, 7, 0},
    {4, 2, 8, 6, 5, 7, 3, 9, 0, 1},
    {2, 7, 9, 3, 8, 0, 6, 4, 1, 5},
    {7, 0, 4, 6, 9, 1, 3, 2, 5, 8}
  };

  /** Verhoeff inverse table (unused for plain validation but part of the canonical tables). */
  private static final int[] INV = {0, 4, 3, 2, 1, 5, 6, 7, 8, 9};

  public IndiaAadhaarDetector() {
    super(PiiType.INDIA_AADHAAR, 8, AADHAAR);
  }

  @Override
  protected boolean accept(Matcher matcher) {
    String digits = matcher.group().replaceAll("\\s", "");
    return digits.length() == 12 && verhoeffValid(digits);
  }

  /** True if the all-digit string (including its trailing check digit) satisfies Verhoeff. */
  private static boolean verhoeffValid(String digits) {
    int c = 0;
    for (int i = 0; i < digits.length(); i++) {
      int d = digits.charAt(digits.length() - 1 - i) - '0';
      c = D[c][P[i % 8][d]];
    }
    return c == 0;
  }
}

package com.gatewise.gateway.redaction;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Detects Australian Tax File Numbers (TFN, 8-9 digits), validated with the ATO weighted-modulus
 * checksum so random digit runs aren't masked.
 */
@Component
public class AuTfnDetector extends RegexDetector {

  private static final Pattern TFN = Pattern.compile("\\b\\d{3}\\s?\\d{3}\\s?\\d{2,3}\\b");

  /** ATO TFN positional weights; an 8-digit TFN uses the first eight. */
  private static final int[] WEIGHTS = {1, 4, 3, 7, 5, 8, 6, 9, 10};

  public AuTfnDetector() {
    super(PiiType.AU_TFN, 7, TFN);
  }

  @Override
  protected boolean accept(Matcher matcher) {
    String digits = matcher.group().replaceAll("\\s", "");
    int len = digits.length();
    if (len != 8 && len != 9) {
      return false;
    }
    int sum = 0;
    for (int i = 0; i < len; i++) {
      sum += (digits.charAt(i) - '0') * WEIGHTS[i];
    }
    return sum % 11 == 0;
  }
}

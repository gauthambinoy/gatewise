package com.gatewise.gateway.redaction;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Detects Canadian Social Insurance Numbers (SIN), validated with the Luhn checksum so random
 * 9-digit runs aren't masked.
 */
@Component
public class CanadaSinDetector extends RegexDetector {

  private static final Pattern SIN = Pattern.compile("\\b\\d{3}[ -]?\\d{3}[ -]?\\d{3}\\b");

  public CanadaSinDetector() {
    super(PiiType.CANADA_SIN, 8, SIN);
  }

  @Override
  protected boolean accept(Matcher matcher) {
    String digits = matcher.group().replaceAll("[ -]", "");
    return digits.length() == 9 && Luhn.isValid(digits);
  }
}

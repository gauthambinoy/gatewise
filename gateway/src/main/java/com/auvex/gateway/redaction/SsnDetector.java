package com.auvex.gateway.redaction;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Detects US Social Security numbers, rejecting structurally invalid area/group/serial blocks. */
@Component
public class SsnDetector extends RegexDetector {

  private static final Pattern SSN = Pattern.compile("\\b(\\d{3})-(\\d{2})-(\\d{4})\\b");

  public SsnDetector() {
    super(PiiType.US_SSN, 8, SSN);
  }

  @Override
  protected boolean accept(Matcher matcher) {
    int area = Integer.parseInt(matcher.group(1));
    if (area == 0 || area == 666 || area >= 900) {
      return false;
    }
    return !matcher.group(2).equals("00") && !matcher.group(3).equals("0000");
  }
}

package com.gatewise.gateway.redaction;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Detects credit-card numbers, validated with the Luhn checksum to reject random digit runs. */
@Component
public class CreditCardDetector extends RegexDetector {

  private static final Pattern CARD = Pattern.compile("\\b(?:\\d[ -]?){12,18}\\d\\b");

  public CreditCardDetector() {
    super(PiiType.CREDIT_CARD, 3, CARD);
  }

  @Override
  protected boolean accept(Matcher matcher) {
    String digits = matcher.group().replaceAll("[ -]", "");
    return digits.length() >= 13 && digits.length() <= 19 && Luhn.isValid(digits);
  }
}

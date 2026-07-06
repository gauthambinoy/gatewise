package com.gatewise.gateway.redaction;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Detects IBANs, validated with the mod-97 checksum so look-alikes don't get masked. */
@Component
public class IbanDetector extends RegexDetector {

  private static final Pattern IBAN = Pattern.compile("\\b[A-Z]{2}\\d{2}[A-Z0-9]{11,30}\\b");

  public IbanDetector() {
    super(PiiType.IBAN, 3, IBAN);
  }

  @Override
  protected boolean accept(Matcher matcher) {
    return Iban.isValid(matcher.group());
  }
}

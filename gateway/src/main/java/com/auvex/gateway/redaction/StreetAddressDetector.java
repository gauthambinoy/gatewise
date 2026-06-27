package com.auvex.gateway.redaction;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Masks a street address: a house number, one to three capitalised name words, and a street-type
 * suffix (Street, Ave, Road, ...). Requiring both the leading number and the suffix keeps it from
 * firing on ordinary prose.
 */
@Component
public class StreetAddressDetector extends RegexDetector {

  private static final Pattern PATTERN =
      Pattern.compile(
          "\\b(\\d{1,5}\\s+(?:[A-Z][A-Za-z.]+\\s+){1,3}"
              + "(?i:street|st|avenue|ave|road|rd|boulevard|blvd|lane|ln|drive|dr|court|ct|way"
              + "|place|pl|terrace|ter|circle|cir|highway|hwy)\\b\\.?)");

  public StreetAddressDetector() {
    super(PiiType.STREET_ADDRESS, 6, PATTERN);
  }

  @Override
  protected int maskGroup() {
    return 1;
  }
}

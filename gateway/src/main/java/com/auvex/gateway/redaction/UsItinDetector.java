package com.auvex.gateway.redaction;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Detects US ITINs: SSN-shaped, area starts with 9, group in 50-65, 70-88, 90-92 or 94-99. */
@Component
public class UsItinDetector extends RegexDetector {

  private static final Pattern ITIN = Pattern.compile("\\b9\\d{2}-(\\d{2})-\\d{4}\\b");

  public UsItinDetector() {
    super(PiiType.US_ITIN, 8, ITIN);
  }

  @Override
  protected boolean accept(Matcher matcher) {
    int group = Integer.parseInt(matcher.group(1));
    return (group >= 50 && group <= 65)
        || (group >= 70 && group <= 88)
        || (group >= 90 && group <= 92)
        || (group >= 94 && group <= 99);
  }
}

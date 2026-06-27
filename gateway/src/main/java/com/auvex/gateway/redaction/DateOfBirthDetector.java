package com.auvex.gateway.redaction;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Masks a date of birth, anchored on a "DOB" / "date of birth" / "born on" label so it only redacts
 * a date that is actually someone's birth date rather than every date in the text.
 */
@Component
public class DateOfBirthDetector extends RegexDetector {

  private static final Pattern PATTERN =
      Pattern.compile(
          "(?i:date of birth|dob|d\\.o\\.b\\.?|born(?:\\s+on)?)\\s*[:\\-]?\\s+"
              + "((?:\\d{1,2}[/\\-.]\\d{1,2}[/\\-.]\\d{2,4})"
              + "|(?:[A-Z][a-z]{2,8}\\.?\\s+\\d{1,2},?\\s+\\d{4}))");

  public DateOfBirthDetector() {
    super(PiiType.DATE_OF_BIRTH, 7, PATTERN);
  }

  @Override
  protected int maskGroup() {
    return 1;
  }
}

package com.gatewise.gateway.redaction;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Masks a person's name, but only when it's clearly labelled — after "my name is", a "name:" field,
 * or a salutation (Mr/Mrs/Ms/Dr/Prof). Anchoring on that context keeps precision high: it never
 * redacts ordinary capitalised words the way a naive free-text name model would.
 */
@Component
public class PersonNameDetector extends RegexDetector {

  private static final Pattern PATTERN =
      Pattern.compile(
          "(?:(?i:my name is)\\s+"
              + "|(?i:full name|patient(?:'s)? name|customer name|name)\\s*[:\\-]\\s*"
              + "|(?i:mr|mrs|ms|miss|dr|prof)\\.?\\s+)"
              + "([A-Z][a-z]+(?:\\s+[A-Z][a-z'.\\-]+){1,2})");

  public PersonNameDetector() {
    super(PiiType.PERSON_NAME, 7, PATTERN);
  }

  @Override
  protected int maskGroup() {
    return 1;
  }
}

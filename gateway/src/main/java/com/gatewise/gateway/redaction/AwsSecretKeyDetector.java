package com.gatewise.gateway.redaction;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Detects AWS secret access keys.
 *
 * <p>A bare 40-char base64 blob is low-signal (any hash matches), so this is keyword-anchored: it
 * only fires when a secret-ish label precedes the value, and it masks just the value (group 1). It
 * has the weakest priority so genuine cards/JWTs/PEMs win any overlap.
 */
@Component
public class AwsSecretKeyDetector extends RegexDetector {

  private static final Pattern SECRET =
      Pattern.compile(
          "(?i)(?:aws_secret_access_key|aws_secret|secret_key)"
              + "[\"'\\s:=]{1,4}([A-Za-z0-9/+=]{40})");

  public AwsSecretKeyDetector() {
    super(PiiType.AWS_SECRET_KEY, 9, SECRET);
  }

  @Override
  protected int maskGroup() {
    return 1;
  }
}

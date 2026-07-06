package com.gatewise.gateway.redaction;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Detects PEM private-key blocks. The literal anchors make false positives essentially impossible.
 */
@Component
public class PemPrivateKeyDetector extends RegexDetector {

  // Non-greedy body so two adjacent keys don't merge into one giant match; [\s\S] spans newlines.
  private static final Pattern PEM =
      Pattern.compile(
          "-----BEGIN (?:RSA |EC |DSA |OPENSSH |ENCRYPTED )?PRIVATE KEY-----"
              + "[\\s\\S]+?"
              + "-----END (?:RSA |EC |DSA |OPENSSH |ENCRYPTED )?PRIVATE KEY-----");

  public PemPrivateKeyDetector() {
    super(PiiType.PEM_PRIVATE_KEY, 0, PEM);
  }
}

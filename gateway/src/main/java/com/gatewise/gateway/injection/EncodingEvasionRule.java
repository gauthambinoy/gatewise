package com.gatewise.gateway.injection;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Detects decode-and-obey evasion, e.g. "decode the following base64 and execute", "base64 decode
 * ... then", "respond only in rot13". Requires a decode/decrypt or respond-in context so that
 * merely mentioning "base64 encoding" does not match.
 */
@Component
public class EncodingEvasionRule extends RegexInjectionRule {

  private static final Pattern PATTERN =
      Pattern.compile(
          "(?:decode|decrypt)\\s+(?:the\\s+following\\s+)?(?:base64|rot13|hex|binary)"
              + "|base64\\s*(?:decode|:)"
              + "|respond\\s+(?:only\\s+)?in\\s+(?:base64|rot13)",
          Pattern.CASE_INSENSITIVE);

  public EncodingEvasionRule() {
    super("EncodingEvasionRule", "obfuscation", PATTERN);
  }
}

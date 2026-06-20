package com.auvex.gateway.redaction;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Detects AWS access key IDs (a known prefix + 16 chars → near-zero false positives). */
@Component
public class AwsAccessKeyDetector extends RegexDetector {

  private static final Pattern KEY =
      Pattern.compile("\\b(?:AKIA|ASIA|AGPA|AIDA|AROA|AIPA|ANPA|ANVA)[A-Z0-9]{16}\\b");

  public AwsAccessKeyDetector() {
    super(PiiType.AWS_ACCESS_KEY_ID, 2, KEY);
  }
}

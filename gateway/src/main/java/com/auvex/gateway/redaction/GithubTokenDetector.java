package com.auvex.gateway.redaction;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Detects GitHub tokens — classic/fine-grained PATs, OAuth, app, and server tokens.
 *
 * <p>High priority (9) so these high-entropy secrets win overlaps with the generic ApiKeyDetector.
 */
@Component
public class GithubTokenDetector extends RegexDetector {

  private static final Pattern GITHUB_TOKEN =
      Pattern.compile("\\b(?:gh[posru])_[A-Za-z0-9]{36}\\b|\\bgithub_pat_[A-Za-z0-9_]{22,}\\b");

  public GithubTokenDetector() {
    super(PiiType.GITHUB_TOKEN, 9, GITHUB_TOKEN);
  }
}

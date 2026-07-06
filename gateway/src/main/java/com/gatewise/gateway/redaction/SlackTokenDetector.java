package com.gatewise.gateway.redaction;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Detects Slack tokens (bot {@code xoxb}, app {@code xoxa}, user {@code xoxp}, refresh {@code
 * xoxr}, and {@code xoxs} session tokens).
 *
 * <p>High priority (9) so these high-entropy secrets win overlaps with the generic ApiKeyDetector.
 */
@Component
public class SlackTokenDetector extends RegexDetector {

  private static final Pattern SLACK_TOKEN = Pattern.compile("\\bxox[baprs]-[A-Za-z0-9-]{10,}\\b");

  public SlackTokenDetector() {
    super(PiiType.SLACK_TOKEN, 9, SLACK_TOKEN);
  }
}

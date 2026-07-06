package com.gatewise.gateway.redaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Behavioural tests for {@link SlackTokenDetector}. */
class SlackTokenDetectorTest {

  private final RedactionEngine engine =
      new RedactionEngine(List.of(new SlackTokenDetector()), new TokenMasker());

  @Test
  void masksSlackToken() {
    String token = "xoxb-1234567890-AbCdEfGhIj";
    assertThat(engine.redact("slack " + token + " end").masked())
        .contains("‹SLACK_TOKEN_REDACTED›")
        .doesNotContain(token);
  }

  @Test
  void leavesCleanTextUntouched() {
    String clean = "The quick brown fox jumps over the lazy dog.";
    RedactionResult result = engine.redact(clean);
    assertThat(result.changed()).isFalse();
    assertThat(result.masked()).isEqualTo(clean);
  }
}

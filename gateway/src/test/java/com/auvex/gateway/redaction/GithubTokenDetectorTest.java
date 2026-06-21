package com.auvex.gateway.redaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Behavioural tests for {@link GithubTokenDetector}. */
class GithubTokenDetectorTest {

  private final RedactionEngine engine =
      new RedactionEngine(List.of(new GithubTokenDetector()), new TokenMasker());

  @Test
  void masksGithubToken() {
    String token = "ghp_ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    assertThat(engine.redact("token " + token + " end").masked())
        .contains("‹GITHUB_TOKEN_REDACTED›")
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

package com.gatewise.gateway.redaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Behavioural tests for {@link GoogleApiKeyDetector}. */
class GoogleApiKeyDetectorTest {

  private final RedactionEngine engine =
      new RedactionEngine(List.of(new GoogleApiKeyDetector()), new TokenMasker());

  @Test
  void masksGoogleApiKey() {
    String key = "AIza" + "0123456789abcdefghijklmnopqrstuvwxy"; // AIza + 35 chars
    RedactionResult result = engine.redact("key " + key + " here");
    assertThat(result.changed()).isTrue();
    assertThat(result.masked()).doesNotContain(key).contains("GOOGLE_API_KEY_REDACTED");
    assertThat(result.countsByType()).containsEntry(PiiType.GOOGLE_API_KEY, 1L);
  }

  @Test
  void leavesCleanTextUntouched() {
    String clean = "The quick brown fox jumps over the lazy dog.";
    RedactionResult result = engine.redact(clean);
    assertThat(result.changed()).isFalse();
    assertThat(result.masked()).isEqualTo(clean);
  }
}

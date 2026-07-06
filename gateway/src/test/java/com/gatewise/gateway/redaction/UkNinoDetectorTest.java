package com.gatewise.gateway.redaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for {@link UkNinoDetector}. */
class UkNinoDetectorTest {

  private final RedactionEngine engine =
      new RedactionEngine(List.of(new UkNinoDetector()), new TokenMasker());

  @Test
  void masksNino() {
    RedactionResult result = engine.redact("NI number AB123456C.");
    assertThat(result.changed()).isTrue();
    assertThat(result.masked()).doesNotContain("AB123456C").contains("UK_NINO_REDACTED");
    assertThat(result.countsByType()).containsEntry(PiiType.UK_NINO, 1L);
  }

  @Test
  void leavesCleanProseUntouched() {
    String clean = "The quick brown fox jumps over the lazy dog.";
    RedactionResult result = engine.redact(clean);
    assertThat(result.changed()).isFalse();
    assertThat(result.masked()).isEqualTo(clean);
  }
}

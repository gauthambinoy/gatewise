package com.auvex.gateway.redaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for {@link IndiaPanDetector}. */
class IndiaPanDetectorTest {

  private final RedactionEngine engine =
      new RedactionEngine(List.of(new IndiaPanDetector()), new TokenMasker());

  @Test
  void masksPan() {
    RedactionResult result = engine.redact("PAN ABCDE1234F");
    assertThat(result.changed()).isTrue();
    assertThat(result.masked()).doesNotContain("ABCDE1234F").contains("PAN_REDACTED");
    assertThat(result.countsByType()).containsEntry(PiiType.INDIA_PAN, 1L);
  }

  @Test
  void leavesCleanProseUntouched() {
    String clean = "The quick brown fox jumps over the lazy dog.";
    RedactionResult result = engine.redact(clean);
    assertThat(result.changed()).isFalse();
    assertThat(result.masked()).isEqualTo(clean);
  }
}

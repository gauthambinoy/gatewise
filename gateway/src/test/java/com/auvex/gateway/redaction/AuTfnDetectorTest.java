package com.auvex.gateway.redaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies the AU TFN detector masks numbers passing the weighted checksum and rejects others. */
class AuTfnDetectorTest {

  private final RedactionEngine engine =
      new RedactionEngine(List.of(new AuTfnDetector()), new TokenMasker());

  @Test
  void masksValidTfn() {
    // 123 456 782: weighted sum is divisible by 11.
    RedactionResult result = engine.redact("my tfn is 123 456 782 ok");
    assertThat(result.changed()).isTrue();
    assertThat(result.masked()).doesNotContain("123 456 782").contains("TFN_REDACTED");
    assertThat(result.countsByType()).containsEntry(PiiType.AU_TFN, 1L);
  }

  @Test
  void masksUnspacedTfn() {
    assertThat(engine.redact("tfn 123456782 end").masked())
        .doesNotContain("123456782")
        .contains("TFN_REDACTED");
  }

  @Test
  void leavesInvalidTfnUntouched() {
    // 123 456 789: weighted sum is not divisible by 11.
    RedactionResult result = engine.redact("ref 123 456 789 only");
    assertThat(result.changed()).isFalse();
    assertThat(result.masked()).contains("123 456 789");
  }
}

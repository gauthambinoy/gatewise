package com.gatewise.gateway.redaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies the Aadhaar detector masks Verhoeff-valid numbers and rejects invalid ones. */
class IndiaAadhaarDetectorTest {

  private final RedactionEngine engine =
      new RedactionEngine(List.of(new IndiaAadhaarDetector()), new TokenMasker());

  @Test
  void masksVerhoeffValidAadhaar() {
    // 2341 2341 2346 passes the Verhoeff checksum and starts with 2 (regex [2-9]).
    RedactionResult result = engine.redact("aadhaar 2341 2341 2346 here");
    assertThat(result.changed()).isTrue();
    assertThat(result.masked()).doesNotContain("2341 2341 2346").contains("AADHAAR_REDACTED");
    assertThat(result.countsByType()).containsEntry(PiiType.INDIA_AADHAAR, 1L);
  }

  @Test
  void masksUnspacedAadhaar() {
    assertThat(engine.redact("id 234123412346 end").masked())
        .doesNotContain("234123412346")
        .contains("AADHAAR_REDACTED");
  }

  @Test
  void leavesVerhoeffInvalidNumberUntouched() {
    // 2341 2341 2347 fails the Verhoeff checksum.
    RedactionResult result = engine.redact("ref 2341 2341 2347 only");
    assertThat(result.changed()).isFalse();
    assertThat(result.masked()).contains("2341 2341 2347");
  }
}

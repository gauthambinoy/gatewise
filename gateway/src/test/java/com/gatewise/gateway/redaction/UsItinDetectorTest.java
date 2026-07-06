package com.gatewise.gateway.redaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for {@link UsItinDetector}. */
class UsItinDetectorTest {

  private final RedactionEngine engine =
      new RedactionEngine(List.of(new UsItinDetector()), new TokenMasker());

  @Test
  void masksItin() {
    RedactionResult result = engine.redact("itin 912-78-1234");
    assertThat(result.changed()).isTrue();
    assertThat(result.masked()).doesNotContain("912-78-1234").contains("ITIN_REDACTED");
    assertThat(result.countsByType()).containsEntry(PiiType.US_ITIN, 1L);
  }

  @Test
  void leavesNonItinSsnUntouched() {
    RedactionResult result = engine.redact("ssn 123-45-6789");
    assertThat(result.changed()).isFalse();
    assertThat(result.masked()).contains("123-45-6789");
  }
}

package com.gatewise.gateway.redaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies the Canada SIN detector masks Luhn-valid SINs and leaves non-Luhn numbers alone. */
class CanadaSinDetectorTest {

  private final RedactionEngine engine =
      new RedactionEngine(List.of(new CanadaSinDetector()), new TokenMasker());

  @Test
  void masksLuhnValidSin() {
    // 046 454 286 passes the Luhn checksum.
    RedactionResult result = engine.redact("my sin is 046 454 286 thanks");
    assertThat(result.changed()).isTrue();
    assertThat(result.masked()).doesNotContain("046 454 286").contains("SIN_REDACTED");
    assertThat(result.countsByType()).containsEntry(PiiType.CANADA_SIN, 1L);
  }

  @Test
  void masksDashedSin() {
    assertThat(engine.redact("sin 046-454-286 end").masked())
        .doesNotContain("046-454-286")
        .contains("SIN_REDACTED");
  }

  @Test
  void leavesNonLuhnNumberUntouched() {
    // 123456789 fails the Luhn checksum.
    RedactionResult result = engine.redact("ref 123456789 only");
    assertThat(result.changed()).isFalse();
    assertThat(result.masked()).contains("123456789");
  }
}

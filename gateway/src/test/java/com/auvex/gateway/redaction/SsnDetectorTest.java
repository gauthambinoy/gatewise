package com.auvex.gateway.redaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for {@link SsnDetector}: masks valid SSNs, leaves invalid/look-alike text untouched. */
class SsnDetectorTest {

  private final RedactionEngine engine =
      new RedactionEngine(List.of(new SsnDetector()), new TokenMasker());

  @Test
  void masksValidSsn() {
    RedactionResult result = engine.redact("my ssn is 123-45-6789.");
    assertThat(result.changed()).isTrue();
    assertThat(result.masked()).contains("SSN_REDACTED").doesNotContain("123-45-6789");
  }

  @Test
  void leavesNonSsnNumbersUntouched() {
    RedactionResult result = engine.redact("id 12345");
    assertThat(result.changed()).isFalse();
    assertThat(result.masked()).isEqualTo("id 12345");
  }

  @Test
  void rejectsInvalidArea() {
    RedactionResult result = engine.redact("000-12-3456");
    assertThat(result.changed()).isFalse();
    assertThat(result.masked()).isEqualTo("000-12-3456");
  }
}

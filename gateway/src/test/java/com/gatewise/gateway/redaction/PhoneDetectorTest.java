package com.gatewise.gateway.redaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for {@link PhoneDetector}: masks phone numbers, leaves bare years untouched. */
class PhoneDetectorTest {

  private final RedactionEngine engine =
      new RedactionEngine(List.of(new PhoneDetector()), new TokenMasker());

  @Test
  void masksPhoneNumber() {
    RedactionResult result = engine.redact("call +1 (415) 555-2671 today");
    assertThat(result.changed()).isTrue();
    assertThat(result.masked()).contains("PHONE_REDACTED").doesNotContain("555-2671");
  }

  @Test
  void leavesYearUntouched() {
    RedactionResult result = engine.redact("the year 2024 was fine");
    assertThat(result.changed()).isFalse();
    assertThat(result.masked()).isEqualTo("the year 2024 was fine");
  }
}

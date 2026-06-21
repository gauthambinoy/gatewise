package com.auvex.gateway.redaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Behavioural tests for {@link MacAddressDetector}. */
class MacAddressDetectorTest {

  private final RedactionEngine engine =
      new RedactionEngine(List.of(new MacAddressDetector()), new TokenMasker());

  @Test
  void masksMacAddress() {
    RedactionResult result = engine.redact("nic 00:1A:2B:3C:4D:5E up");
    assertThat(result.changed()).isTrue();
    assertThat(result.masked()).doesNotContain("00:1A:2B:3C:4D:5E").contains("MAC_REDACTED");
    assertThat(result.countsByType()).containsEntry(PiiType.MAC_ADDRESS, 1L);
  }

  @Test
  void leavesCleanTextUntouched() {
    String clean = "The quick brown fox jumps over the lazy dog.";
    RedactionResult result = engine.redact(clean);
    assertThat(result.changed()).isFalse();
    assertThat(result.masked()).isEqualTo(clean);
  }
}

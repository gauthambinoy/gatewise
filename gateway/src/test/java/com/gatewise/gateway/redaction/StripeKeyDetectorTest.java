package com.gatewise.gateway.redaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Behavioural tests for {@link StripeKeyDetector}. */
class StripeKeyDetectorTest {

  private final RedactionEngine engine =
      new RedactionEngine(List.of(new StripeKeyDetector()), new TokenMasker());

  @Test
  void masksStripeKey() {
    String key = "sk_live_" + "abcdEFGH1234567890ZyXwVu"; // sk_live_ + 24 chars
    RedactionResult result = engine.redact("secret " + key + " end");
    assertThat(result.changed()).isTrue();
    assertThat(result.masked()).doesNotContain(key).contains("STRIPE_KEY_REDACTED");
    assertThat(result.countsByType()).containsEntry(PiiType.STRIPE_KEY, 1L);
  }

  @Test
  void leavesCleanTextUntouched() {
    String clean = "The quick brown fox jumps over the lazy dog.";
    RedactionResult result = engine.redact(clean);
    assertThat(result.changed()).isFalse();
    assertThat(result.masked()).isEqualTo(clean);
  }
}

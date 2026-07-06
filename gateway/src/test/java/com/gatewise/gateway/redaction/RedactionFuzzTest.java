package com.gatewise.gateway.redaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/**
 * Fuzz tests for the redaction engine: adversarial unicode and very large bodies must never throw
 * and must finish (the engine is a single linear pass), with output that stays deterministic.
 */
class RedactionFuzzTest {

  private final RedactionEngine engine =
      new RedactionEngine(
          List.of(
              new PemPrivateKeyDetector(),
              new JwtDetector(),
              new AwsAccessKeyDetector(),
              new CreditCardDetector(),
              new IbanDetector(),
              new EmailDetector(),
              new ApiKeyDetector(),
              new AwsSecretKeyDetector()),
          new TokenMasker());

  /** Arbitrary unicode (including lone surrogates and control chars) never throws and is stable. */
  @Property(tries = 500)
  void arbitraryUnicodeNeverThrowsAndIsDeterministic(@ForAll("anyText") String text) {
    String masked = engine.redact(text).masked();
    assertThat(masked).isNotNull();
    assertThat(engine.redact(text).masked()).isEqualTo(masked);
  }

  /** A very large clean body completes and is returned untouched (the linear no-op path). */
  @Property(tries = 40)
  void hugeCleanBodyIsReturnedUnchanged(@ForAll @IntRange(min = 1000, max = 50000) int size) {
    String unit = "the quick brown fox jumps over the lazy dog ";
    StringBuilder body = new StringBuilder(size + unit.length());
    while (body.length() < size) {
      body.append(unit);
    }
    String text = body.toString();
    RedactionResult result = engine.redact(text);
    assertThat(result.changed()).isFalse();
    assertThat(result.masked()).isEqualTo(text);
  }

  /** A large body with many scattered emails completes and leaks none of them. */
  @Property(tries = 40)
  void hugeBodyWithScatteredSecretsLeaksNothing(@ForAll @IntRange(min = 50, max = 800) int count) {
    StringBuilder body = new StringBuilder();
    for (int i = 0; i < count; i++) {
      body.append("line ").append(i).append(" reach user").append(i).append("@corp.example now\n");
    }
    RedactionResult result = engine.redact(body.toString());
    assertThat(result.changed()).isTrue();
    assertThat(result.masked()).doesNotContain("@corp.example");
  }

  @Provide
  Arbitrary<String> anyText() {
    return Arbitraries.strings().all().ofMaxLength(3000);
  }

  // A second guard that the engine never throws on any unicode, expressed directly.
  @Property(tries = 500)
  void redactNeverThrowsOnAnyInput(@ForAll("anyText") String text) {
    assertThatCode(() -> engine.redact(text)).doesNotThrowAnyException();
  }
}

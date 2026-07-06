package com.gatewise.gateway.redaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based tests for the redaction engine: invariants that must hold for any generated input,
 * not just the hand-picked vectors in {@link RedactionEngineTest}.
 */
class RedactionPropertyTest {

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

  /** Random letters-and-spaces text carries no PII, so it must pass through byte-for-byte. */
  @Property
  void cleanTextPassesThroughUnchanged(@ForAll("cleanText") String text) {
    RedactionResult result = engine.redact(text);
    assertThat(result.changed()).isFalse();
    assertThat(result.masked()).isEqualTo(text);
    assertThat(result.count()).isZero();
  }

  /** A planted email embedded in clean text is detected and never survives in the output. */
  @Property
  void plantedEmailNeverLeaks(
      @ForAll("cleanText") String before,
      @ForAll("emails") String email,
      @ForAll("cleanText") String after) {
    RedactionResult result = engine.redact(before + " " + email + " " + after);
    assertThat(result.changed()).isTrue();
    assertThat(result.masked()).doesNotContain(email);
  }

  /** A planted Luhn-valid card embedded in clean text is detected and never survives. */
  @Property
  void plantedCardNeverLeaks(
      @ForAll("cleanText") String before,
      @ForAll("cards") String card,
      @ForAll("cleanText") String after) {
    RedactionResult result = engine.redact(before + " " + card + " " + after);
    assertThat(result.changed()).isTrue();
    assertThat(result.masked()).doesNotContain(card);
  }

  /** A planted AWS access key embedded in clean text is detected and never survives. */
  @Property
  void plantedAwsKeyNeverLeaks(
      @ForAll("cleanText") String before,
      @ForAll("awsKeys") String key,
      @ForAll("cleanText") String after) {
    RedactionResult result = engine.redact(before + " " + key + " " + after);
    assertThat(result.changed()).isTrue();
    assertThat(result.masked()).doesNotContain(key);
  }

  /** Redacting already-redacted text is a no-op: the mask tokens are never re-matched. */
  @Property
  void redactionIsIdempotent(
      @ForAll("cleanText") String a,
      @ForAll("emails") String email,
      @ForAll("cleanText") String b,
      @ForAll("cards") String card,
      @ForAll("cleanText") String c) {
    String once = engine.redact(a + " " + email + " " + b + " " + card + " " + c).masked();
    String twice = engine.redact(once).masked();
    assertThat(twice).isEqualTo(once);
  }

  // --- Generators ---------------------------------------------------------------------------

  @Provide
  Arbitrary<String> cleanText() {
    return Arbitraries.strings()
        .withCharRange('a', 'z')
        .ofMinLength(1)
        .ofMaxLength(8)
        .list()
        .ofMaxSize(20)
        .map(words -> String.join(" ", words));
  }

  @Provide
  Arbitrary<String> emails() {
    Arbitrary<String> local =
        Arbitraries.strings().withCharRange('a', 'z').ofMinLength(3).ofMaxLength(10);
    Arbitrary<String> domain =
        Arbitraries.strings().withCharRange('a', 'z').ofMinLength(3).ofMaxLength(10);
    Arbitrary<String> tld =
        Arbitraries.strings().withCharRange('a', 'z').ofMinLength(2).ofMaxLength(6);
    return Combinators.combine(local, domain, tld).as((l, d, t) -> l + "@" + d + "." + t);
  }

  @Provide
  Arbitrary<String> cards() {
    return Arbitraries.strings()
        .withCharRange('0', '9')
        .ofLength(15)
        .map(RedactionPropertyTest::withLuhnCheckDigit);
  }

  @Provide
  Arbitrary<String> awsKeys() {
    return Arbitraries.strings()
        .withChars("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
        .ofLength(16)
        .map(suffix -> "AKIA" + suffix);
  }

  // Appends the single Luhn check digit (using the production validator as the oracle) so the
  // generated 16-digit string is a card the CreditCardDetector will accept.
  private static String withLuhnCheckDigit(String base15) {
    for (int x = 0; x <= 9; x++) {
      String candidate = base15 + x;
      if (Luhn.isValid(candidate)) {
        return candidate;
      }
    }
    throw new IllegalStateException("no Luhn check digit for " + base15);
  }
}

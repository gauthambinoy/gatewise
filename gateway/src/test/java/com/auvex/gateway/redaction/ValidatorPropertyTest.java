package com.auvex.gateway.redaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based tests for the {@link Luhn} and {@link Iban} checksums: generated valid samples are
 * accepted, and any single-digit mutation of a valid sample is rejected (the checksum property).
 */
class ValidatorPropertyTest {

  @Property
  void luhnAcceptsValidCardsAndRejectsSingleDigitMutations(
      @ForAll("digits15") String base,
      @ForAll @IntRange(min = 0, max = 15) int index,
      @ForAll @IntRange(min = 1, max = 9) int delta) {
    String card = withLuhnCheckDigit(base);
    assertThat(Luhn.isValid(card)).isTrue();

    String mutated = mutateDigit(card, index, delta);
    assertThat(mutated).isNotEqualTo(card);
    assertThat(Luhn.isValid(mutated)).isFalse();
  }

  @Property
  void ibanAcceptsValidSamplesAndRejectsSingleDigitMutations(
      @ForAll("ibans") String iban,
      @ForAll @IntRange(min = 2, max = 3) int checkDigitIndex,
      @ForAll @IntRange(min = 1, max = 9) int delta) {
    assertThat(Iban.isValid(iban)).isTrue();

    // Mutate one of the two check digits: a single digit change always breaks mod-97.
    String mutated = mutateDigit(iban, checkDigitIndex, delta);
    assertThat(mutated).isNotEqualTo(iban);
    assertThat(Iban.isValid(mutated)).isFalse();
  }

  // --- Generators ---------------------------------------------------------------------------

  @Provide
  Arbitrary<String> digits15() {
    return Arbitraries.strings().withCharRange('0', '9').ofLength(15);
  }

  @Provide
  Arbitrary<String> ibans() {
    Arbitrary<String> country = Arbitraries.strings().withCharRange('A', 'Z').ofLength(2);
    Arbitrary<String> bban =
        Arbitraries.strings()
            .withChars("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
            .ofMinLength(11)
            .ofMaxLength(28);
    return Combinators.combine(country, bban).as(ValidatorPropertyTest::withIbanCheckDigits);
  }

  // --- Helpers ------------------------------------------------------------------------------

  // Changes the digit at {@code index} to a guaranteed-different digit.
  private static String mutateDigit(String value, int index, int delta) {
    char[] chars = value.toCharArray();
    int original = chars[index] - '0';
    chars[index] = (char) ('0' + (original + delta) % 10);
    return new String(chars);
  }

  // Appends the single Luhn check digit using the production validator as the oracle.
  private static String withLuhnCheckDigit(String base15) {
    for (int x = 0; x <= 9; x++) {
      String candidate = base15 + x;
      if (Luhn.isValid(candidate)) {
        return candidate;
      }
    }
    throw new IllegalStateException("no Luhn check digit for " + base15);
  }

  // Finds the two mod-97 check digits that make a valid IBAN, using the production validator.
  private static String withIbanCheckDigits(String country, String bban) {
    for (int cd = 0; cd <= 99; cd++) {
      String candidate = country + String.format(Locale.ROOT, "%02d", cd) + bban;
      if (Iban.isValid(candidate)) {
        return candidate;
      }
    }
    throw new IllegalStateException("no IBAN check digits for " + country + " " + bban);
  }
}

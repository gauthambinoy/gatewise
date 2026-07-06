package com.gatewise.gateway.redaction;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unit tests for the IBAN mod-97 checksum. */
class IbanTest {

  @Test
  void acceptsValidIbans() {
    assertThat(Iban.isValid("DE89370400440532013000")).isTrue();
    assertThat(Iban.isValid("GB82 WEST 1234 5698 7654 32")).isTrue();
  }

  @Test
  void rejectsInvalidIbans() {
    assertThat(Iban.isValid("GB00WEST12345698765432")).isFalse();
    assertThat(Iban.isValid("")).isFalse();
    assertThat(Iban.isValid("NOTANIBAN")).isFalse();
  }
}

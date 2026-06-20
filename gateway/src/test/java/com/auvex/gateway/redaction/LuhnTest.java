package com.auvex.gateway.redaction;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unit tests for the Luhn checksum. */
class LuhnTest {

  @Test
  void acceptsValidCardNumbers() {
    assertThat(Luhn.isValid("4111111111111111")).isTrue();
    assertThat(Luhn.isValid("4012888888881881")).isTrue();
  }

  @Test
  void rejectsInvalidOrJunk() {
    assertThat(Luhn.isValid("1234567890123456")).isFalse();
    assertThat(Luhn.isValid("")).isFalse();
    assertThat(Luhn.isValid("abc")).isFalse();
  }
}

package com.auvex.gateway.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** Unit tests for the AES-256-GCM field cipher: round-trip, fresh IV, pass-through, fail-closed. */
class AesGcmFieldCipherTest {

  // A 32-byte key (AES-256), base64-encoded, as it would arrive from config.
  private static final String KEY =
      Base64.getEncoder()
          .encodeToString("01234567890123456789012345678901".getBytes(StandardCharsets.US_ASCII));

  private final AesGcmFieldCipher enabled = new AesGcmFieldCipher(true, KEY);

  @Test
  void encryptsAndDecryptsBackToTheOriginal() {
    String plaintext = "summarize the quarterly numbers for Acme";

    String stored = enabled.encrypt(plaintext);

    assertThat(stored).startsWith(AesGcmFieldCipher.MARKER).isNotEqualTo(plaintext);
    assertThat(enabled.decrypt(stored)).isEqualTo(plaintext);
  }

  @Test
  void producesDifferentCiphertextEachCallButSamePlaintext() {
    String plaintext = "the same secret";

    String first = enabled.encrypt(plaintext);
    String second = enabled.encrypt(plaintext);

    // A fresh random IV per call means identical input never yields identical stored bytes.
    assertThat(first).isNotEqualTo(second);
    assertThat(enabled.decrypt(first)).isEqualTo(plaintext);
    assertThat(enabled.decrypt(second)).isEqualTo(plaintext);
  }

  @Test
  void passesThroughValuesWithoutTheMarker() {
    // Plaintext rows (written before encryption was switched on) carry no marker and must read back
    // untouched, so toggling encryption on never corrupts existing data.
    assertThat(enabled.decrypt("plain legacy prompt")).isEqualTo("plain legacy prompt");
    assertThat(enabled.decrypt(null)).isNull();
  }

  @Test
  void tamperedCiphertextFailsClosed() {
    String stored = enabled.encrypt("do not alter me");

    byte[] body = Base64.getDecoder().decode(stored.substring(AesGcmFieldCipher.MARKER.length()));
    body[body.length - 1] ^= 0x01; // flip a bit in the GCM auth tag
    String tampered = AesGcmFieldCipher.MARKER + Base64.getEncoder().encodeToString(body);

    assertThatThrownBy(() -> enabled.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void disabledCipherIsPassThrough() {
    AesGcmFieldCipher disabled = new AesGcmFieldCipher(false, null);

    assertThat(disabled.encrypt("nothing happens")).isEqualTo("nothing happens");
    assertThat(disabled.decrypt("nothing happens")).isEqualTo("nothing happens");
  }

  @Test
  void enablingWithoutAKeyIsRejected() {
    assertThatThrownBy(() -> new AesGcmFieldCipher(true, null))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aKeyOfTheWrongLengthIsRejected() {
    String shortKey =
        Base64.getEncoder().encodeToString("too short".getBytes(StandardCharsets.UTF_8));
    assertThatThrownBy(() -> new AesGcmFieldCipher(true, shortKey))
        .isInstanceOf(IllegalStateException.class);
  }
}

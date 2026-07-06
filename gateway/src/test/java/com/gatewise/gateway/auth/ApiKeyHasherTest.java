package com.gatewise.gateway.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unit tests for the API-key hashing used during authentication. */
class ApiKeyHasherTest {

  @Test
  void hashIsDeterministicLowercaseHex() {
    String hash = ApiKeyHasher.hash("gatewise_secret_abc");
    assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
    assertThat(ApiKeyHasher.hash("gatewise_secret_abc")).isEqualTo(hash);
  }

  @Test
  void differentKeysProduceDifferentHashes() {
    assertThat(ApiKeyHasher.hash("key-one")).isNotEqualTo(ApiKeyHasher.hash("key-two"));
  }
}

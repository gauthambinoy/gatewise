package com.auvex.gateway.crypto;

import com.auvex.gateway.config.EncryptionProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the {@link FieldCipher} used to encrypt audit fields at rest. */
@Configuration
public class EncryptionConfig {

  /**
   * The active cipher. The default is AES-256-GCM with a key from {@code auvex.encryption}.
   *
   * <p>This is the one place to bring your own KMS: return a KMS-backed {@link FieldCipher} here
   * instead (for example one that uses AWS KMS to encrypt/decrypt, or to unwrap a data key) and
   * nothing downstream changes, because the audit layer depends only on the interface.
   */
  @Bean
  public FieldCipher fieldCipher(EncryptionProperties properties) {
    return new AesGcmFieldCipher(properties.enabled(), properties.key());
  }
}

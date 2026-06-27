package com.auvex.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Field-level encryption of audit data at rest.
 *
 * @param enabled encrypt the redacted prompt/response columns on write (off by default)
 * @param key base64 of a 32-byte AES-256 key; required when {@code enabled}. Keep it outside the
 *     database it protects (an env var, a secrets manager, a KMS-wrapped data key) — anyone who has
 *     both the key and a database dump can read the data, so they must not live together.
 */
@ConfigurationProperties(prefix = "auvex.encryption")
public record EncryptionProperties(boolean enabled, String key) {}

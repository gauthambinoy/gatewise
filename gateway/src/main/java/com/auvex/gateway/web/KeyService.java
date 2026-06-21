package com.auvex.gateway.web;

import com.auvex.gateway.auth.ApiKeyHasher;
import com.auvex.gateway.tenant.ApiKey;
import com.auvex.gateway.tenant.ApiKeyRepository;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Creates, lists and revokes a tenant's API keys.
 *
 * <p>A new key's raw secret is generated here, returned exactly once, and never stored — only its
 * SHA-256 hash and a short display prefix are persisted.
 */
@Component
public class KeyService {

  private static final SecureRandom RANDOM = new SecureRandom();

  private final ApiKeyRepository keys;

  public KeyService(ApiKeyRepository keys) {
    this.keys = keys;
  }

  public List<ApiKey> list(UUID tenantId) {
    return keys.findByTenantIdOrderByCreatedAtAsc(tenantId);
  }

  /** Mints a new key, returning both the stored record and the raw secret (shown once). */
  public Created create(UUID tenantId, String name) {
    byte[] secret = new byte[24];
    RANDOM.nextBytes(secret);
    String raw = "auvex_sk_" + HexFormat.of().formatHex(secret);
    ApiKey key =
        new ApiKey(tenantId, displayName(name), ApiKeyHasher.hash(raw), raw.substring(0, 16));
    return new Created(keys.save(key), raw);
  }

  public void revoke(UUID tenantId, UUID id) {
    ApiKey key =
        keys.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new NotFoundException("API key " + id + " not found."));
    key.revoke();
    keys.save(key);
  }

  private static String displayName(String name) {
    return name == null || name.isBlank() ? "console-key" : name;
  }

  /** A freshly created key together with its one-time raw secret. */
  public record Created(ApiKey key, String rawKey) {}
}

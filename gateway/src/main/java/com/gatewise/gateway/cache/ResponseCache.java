package com.gatewise.gateway.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatewise.gateway.config.CacheProperties;
import com.gatewise.gateway.proxy.CachedResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * A Redis-backed cache of upstream responses, keyed by tenant + a hash of the (redacted) request.
 *
 * <p>Cache is opt-in via config and fails open: if Redis is unreachable or the stored value can't
 * be read, it behaves as a miss rather than failing the request.
 */
@Component
public class ResponseCache {

  private final StringRedisTemplate redis;
  private final ObjectMapper json;
  private final CacheProperties properties;

  public ResponseCache(StringRedisTemplate redis, ObjectMapper json, CacheProperties properties) {
    this.redis = redis;
    this.json = json;
    this.properties = properties;
  }

  /** Whether the cache should be consulted at all. */
  public boolean enabled() {
    return properties.enabled();
  }

  /** The cache key for a request, scoped to its tenant. */
  public String keyFor(UUID tenantId, byte[] requestBody) {
    return "gatewise:cache:" + tenantId + ':' + sha256Hex(requestBody);
  }

  /** Returns the cached response for a key, or null on a miss (or any Redis/parse failure). */
  public CachedResponse get(String key) {
    if (!enabled()) {
      return null;
    }
    try {
      String stored = redis.opsForValue().get(key);
      return stored == null ? null : json.readValue(stored, CachedResponse.class);
    } catch (JsonProcessingException | RuntimeException e) {
      return null; // fail open — a cache problem must never break the call
    }
  }

  /** Stores a response under a key with the configured TTL. */
  public void put(String key, CachedResponse response) {
    if (!enabled()) {
      return;
    }
    try {
      redis.opsForValue().set(key, json.writeValueAsString(response), properties.ttl());
    } catch (JsonProcessingException | RuntimeException e) {
      // fail open — best-effort caching only
    }
  }

  private static String sha256Hex(byte[] data) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm unavailable", e);
    }
  }
}

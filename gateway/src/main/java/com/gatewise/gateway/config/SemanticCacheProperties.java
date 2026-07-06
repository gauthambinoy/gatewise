package com.gatewise.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Semantic-cache settings — a similarity cache that serves a stored response when a new prompt is
 * close enough to a previous one (complementing the exact-match {@link CacheProperties} cache).
 *
 * @param enabled whether the semantic cache is consulted (off by default)
 * @param threshold minimum cosine similarity (0..1) to count as a hit; defaults to 0.95
 * @param maxEntriesPerTenant how many recent prompt/response pairs to keep per tenant; defaults 200
 */
@ConfigurationProperties(prefix = "gatewise.cache.semantic")
public record SemanticCacheProperties(
    boolean enabled, Double threshold, Integer maxEntriesPerTenant) {

  public SemanticCacheProperties {
    if (threshold == null || threshold <= 0 || threshold > 1) {
      threshold = 0.95;
    }
    if (maxEntriesPerTenant == null || maxEntriesPerTenant <= 0) {
      maxEntriesPerTenant = 200;
    }
  }
}

package com.auvex.gateway.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Response-cache settings.
 *
 * @param enabled whether the cache is consulted at all (off in tests by default)
 * @param ttl how long a cached response lives; defaults to 5 minutes when unset
 */
@ConfigurationProperties(prefix = "auvex.cache")
public record CacheProperties(boolean enabled, Duration ttl) {

  public CacheProperties {
    if (ttl == null) {
      ttl = Duration.ofMinutes(5);
    }
  }
}

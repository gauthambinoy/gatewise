package com.auvex.gateway.ratelimit;

import com.auvex.gateway.config.RateLimitProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * A Redis-backed per-tenant rate limiter: a fixed-window counter per minute, shared across gateway
 * instances. Fail-open — if Redis is unavailable the request is allowed, so the limiter can never
 * take the gateway down.
 */
@Component
public class RateLimiter {

  private static final Logger LOG = LoggerFactory.getLogger(RateLimiter.class);

  private final StringRedisTemplate redis;
  private final RateLimitProperties properties;

  public RateLimiter(StringRedisTemplate redis, RateLimitProperties properties) {
    this.redis = redis;
    this.properties = properties;
  }

  /** Whether this tenant may make another request in the current minute. */
  public boolean allow(UUID tenantId) {
    if (!properties.enabled()) {
      return true;
    }
    try {
      long window = Instant.now().getEpochSecond() / 60;
      String key = "auvex:rl:" + tenantId + ":" + window;
      Long count = redis.opsForValue().increment(key);
      if (count != null && count == 1L) {
        // First hit in this window — expire a little after the window so the key self-cleans.
        redis.expire(key, Duration.ofSeconds(90));
      }
      return count == null || count <= properties.requestsPerMinute();
    } catch (DataAccessException e) {
      LOG.warn("Rate limiter unavailable; allowing request: {}", e.getMessage());
      return true;
    }
  }
}

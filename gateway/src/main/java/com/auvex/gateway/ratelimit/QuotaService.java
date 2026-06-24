package com.auvex.gateway.ratelimit;

import com.auvex.gateway.config.QuotaProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed daily quotas, per caller and per model — a longer-window cap than the per-minute
 * rate limit. Each is a fixed-window counter keyed by the UTC day. Fail-open: if Redis is
 * unavailable the call is allowed, so the quota can never take the gateway down.
 */
@Component
public class QuotaService {

  private static final Logger LOG = LoggerFactory.getLogger(QuotaService.class);
  private static final Duration KEEP = Duration.ofHours(25); // a little over a day, so keys self-clean

  private final StringRedisTemplate redis;
  private final QuotaProperties properties;

  public QuotaService(StringRedisTemplate redis, QuotaProperties properties) {
    this.redis = redis;
    this.properties = properties;
  }

  /**
   * Counts this call against the caller's and the model's daily quotas, throwing {@link
   * QuotaExceededException} if either is exhausted.
   */
  public void check(UUID tenantId, String actor, String model) {
    if (!properties.enabled()) {
      return;
    }
    long day = Instant.now().getEpochSecond() / 86_400;
    if (properties.perUserPerDay() > 0) {
      enforce(
          "auvex:quota:user:" + tenantId + ":" + actor + ":" + day,
          properties.perUserPerDay(),
          "per-user daily quota");
    }
    if (properties.perModelPerDay() > 0) {
      enforce(
          "auvex:quota:model:" + tenantId + ":" + model + ":" + day,
          properties.perModelPerDay(),
          "per-model daily quota");
    }
  }

  private void enforce(String key, int limit, String label) {
    Long count;
    try {
      count = redis.opsForValue().increment(key);
      if (count != null && count == 1L) {
        redis.expire(key, KEEP);
      }
    } catch (DataAccessException e) {
      LOG.warn("Quota store unavailable; allowing call: {}", e.getMessage());
      return; // fail-open
    }
    if (count != null && count > limit) {
      throw new QuotaExceededException("Exceeded " + label + " of " + limit + " calls.");
    }
  }
}

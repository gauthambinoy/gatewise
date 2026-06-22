package com.auvex.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Per-tenant request rate limit (a per-minute burst cap, distinct from the longer-window call
 * budget).
 *
 * @param enabled enforce the limit (default off)
 * @param requestsPerMinute the per-tenant ceiling per minute (defaults to 600 if unset)
 */
@ConfigurationProperties(prefix = "auvex.ratelimit")
public record RateLimitProperties(boolean enabled, int requestsPerMinute) {

  public RateLimitProperties {
    if (requestsPerMinute <= 0) {
      requestsPerMinute = 600;
    }
  }
}

package com.gatewise.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Per-user and per-model daily call quotas (a longer-window cap than the per-minute rate limit).
 *
 * @param enabled enforce the quotas (default off)
 * @param perUserPerDay max calls per caller (the request's {@code user}) per day; 0 = unlimited
 * @param perModelPerDay max calls per resolved model per day; 0 = unlimited
 */
@ConfigurationProperties(prefix = "gatewise.quota")
public record QuotaProperties(boolean enabled, int perUserPerDay, int perModelPerDay) {

  public QuotaProperties {
    if (perUserPerDay < 0) {
      perUserPerDay = 0;
    }
    if (perModelPerDay < 0) {
      perModelPerDay = 0;
    }
  }
}

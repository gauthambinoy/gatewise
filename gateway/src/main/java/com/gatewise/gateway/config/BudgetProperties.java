package com.gatewise.gateway.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Per-tenant call-budget settings.
 *
 * @param enabled whether budgets are enforced at all (off in tests by default)
 * @param maxCalls allowed forwarded calls per window; defaults to 100000 when unset
 * @param window the rolling window the calls are counted over; defaults to one day
 */
@ConfigurationProperties(prefix = "gatewise.budget")
public record BudgetProperties(boolean enabled, int maxCalls, Duration window) {

  public BudgetProperties {
    if (maxCalls <= 0) {
      maxCalls = 100_000;
    }
    if (window == null) {
      window = Duration.ofDays(1);
    }
  }
}

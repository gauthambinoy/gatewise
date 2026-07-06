package com.gatewise.gateway.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cost-anomaly alerting: warn (and notify the webhook) when a tenant's recorded spend crosses a
 * threshold.
 *
 * @param enabled turn the monitor on (default off)
 * @param thresholdUsd the cumulative spend, in USD, that triggers an alert (defaults to 100)
 */
@ConfigurationProperties(prefix = "gatewise.cost-alert")
public record CostAnomalyProperties(boolean enabled, BigDecimal thresholdUsd) {

  public CostAnomalyProperties {
    if (thresholdUsd == null) {
      thresholdUsd = new BigDecimal("100");
    }
  }
}

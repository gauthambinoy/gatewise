package com.gatewise.gateway.config;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Per-model pricing for cost accounting, in USD per 1,000,000 tokens.
 *
 * @param models model name → price; {@code fallback} is used for anything not listed
 * @param fallback the default price when a model isn't in the table
 */
@ConfigurationProperties(prefix = "gatewise.pricing")
public record PricingProperties(Map<String, ModelPrice> models, ModelPrice fallback) {

  public PricingProperties {
    models = models == null ? Map.of() : Map.copyOf(models);
    if (fallback == null) {
      fallback = new ModelPrice(BigDecimal.ONE, new BigDecimal("3"));
    }
  }

  /** Input and output price per million tokens for one model. */
  public record ModelPrice(BigDecimal inputPerMillion, BigDecimal outputPerMillion) {
    public ModelPrice {
      if (inputPerMillion == null) {
        inputPerMillion = BigDecimal.ZERO;
      }
      if (outputPerMillion == null) {
        outputPerMillion = BigDecimal.ZERO;
      }
    }
  }
}

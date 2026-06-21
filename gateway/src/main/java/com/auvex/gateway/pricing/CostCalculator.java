package com.auvex.gateway.pricing;

import com.auvex.gateway.config.PricingProperties;
import com.auvex.gateway.config.PricingProperties.ModelPrice;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/** Computes the USD cost of a call from its token usage and the per-model price. */
@Component
public class CostCalculator {

  private static final BigDecimal MILLION = new BigDecimal("1000000");

  private final PricingProperties pricing;

  public CostCalculator(PricingProperties pricing) {
    this.pricing = pricing;
  }

  /** Cost in USD for the given model and usage, or null when usage is unknown. */
  public BigDecimal cost(String model, TokenUsage usage) {
    if (usage == null) {
      return null;
    }
    ModelPrice price = pricing.models().getOrDefault(model, pricing.fallback());
    BigDecimal input = price.inputPerMillion().multiply(BigDecimal.valueOf(usage.promptTokens()));
    BigDecimal output =
        price.outputPerMillion().multiply(BigDecimal.valueOf(usage.completionTokens()));
    return input.add(output).divide(MILLION, 6, RoundingMode.HALF_UP);
  }
}

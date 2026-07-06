package com.gatewise.gateway.routing;

import com.gatewise.gateway.config.PricingProperties;
import com.gatewise.gateway.config.PricingProperties.ModelPrice;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The default routing strategy: pick the candidate with the lowest estimated cost for the request,
 * keeping the operator's listed order as the tie-break.
 *
 * <p>Cost is estimated from the per-model price and the request size, assuming output of comparable
 * size to input — only the relative order matters for the choice, so this is enough to rank models.
 */
@Component
public class CostAwareRoutingStrategy implements RoutingStrategy {

  private final PricingProperties pricing;

  public CostAwareRoutingStrategy(PricingProperties pricing) {
    this.pricing = pricing;
  }

  @Override
  public String select(List<String> candidates, RoutingContext context) {
    String best = candidates.get(0);
    BigDecimal bestCost = estimate(best, context.estimatedPromptTokens());
    for (String candidate : candidates) {
      BigDecimal cost = estimate(candidate, context.estimatedPromptTokens());
      if (cost.compareTo(bestCost) < 0) {
        best = candidate;
        bestCost = cost;
      }
    }
    return best;
  }

  // A rough per-request cost used only to rank candidates (input + an assumed equal-size output).
  private BigDecimal estimate(String model, int promptTokens) {
    ModelPrice price = pricing.models().getOrDefault(model, pricing.fallback());
    BigDecimal tokens = BigDecimal.valueOf(promptTokens);
    return price.inputPerMillion().multiply(tokens).add(price.outputPerMillion().multiply(tokens));
  }
}

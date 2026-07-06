package com.gatewise.gateway.routing;

import static org.assertj.core.api.Assertions.assertThat;

import com.gatewise.gateway.config.PricingProperties;
import com.gatewise.gateway.config.PricingProperties.ModelPrice;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for the cost-minimising default routing strategy. */
class CostAwareRoutingStrategyTest {

  private CostAwareRoutingStrategy strategyWith(Map<String, ModelPrice> models) {
    return new CostAwareRoutingStrategy(
        new PricingProperties(models, new ModelPrice(new BigDecimal("99"), new BigDecimal("99"))));
  }

  private RoutingContext ctx() {
    return new RoutingContext(UUID.randomUUID(), 1000);
  }

  @Test
  void picksTheCheapestCandidate() {
    CostAwareRoutingStrategy strategy =
        strategyWith(
            Map.of(
                "cheap", new ModelPrice(new BigDecimal("0.1"), new BigDecimal("0.2")),
                "pricey", new ModelPrice(new BigDecimal("5"), new BigDecimal("15"))));

    assertThat(strategy.select(List.of("pricey", "cheap"), ctx())).isEqualTo("cheap");
  }

  @Test
  void keepsTheListedOrderOnATie() {
    CostAwareRoutingStrategy strategy =
        strategyWith(
            Map.of(
                "a", new ModelPrice(BigDecimal.ONE, BigDecimal.ONE),
                "b", new ModelPrice(BigDecimal.ONE, BigDecimal.ONE)));

    assertThat(strategy.select(List.of("a", "b"), ctx())).isEqualTo("a");
  }

  @Test
  void anUnpricedModelFallsBackToTheFallbackPrice() {
    // "known" is far cheaper than the fallback (99/99), so it beats an unpriced candidate.
    CostAwareRoutingStrategy strategy =
        strategyWith(Map.of("known", new ModelPrice(new BigDecimal("0.5"), new BigDecimal("0.5"))));

    assertThat(strategy.select(List.of("unpriced", "known"), ctx())).isEqualTo("known");
  }
}

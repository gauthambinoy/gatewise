package com.auvex.gateway.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for smart routing: pool selection when on, the static table when off. */
class ModelRouterSmartTest {

  // A stub strategy that picks the last candidate, so we can tell the router consulted it.
  private static final RoutingStrategy PICK_LAST =
      (candidates, context) -> candidates.get(candidates.size() - 1);

  private RoutingContext ctx() {
    return new RoutingContext(UUID.randomUUID(), 100);
  }

  @Test
  void usesTheStaticTableWhenSmartRoutingIsOff() {
    ModelRouter router =
        new ModelRouter(
            new ModelRoutingProperties(Map.of("fast", "openai/gpt-4o-mini")),
            new SmartRoutingProperties(false, Map.of("fast", List.of("a", "b"))),
            PICK_LAST);

    assertThat(router.resolve("fast", ctx())).isEqualTo("openai/gpt-4o-mini");
  }

  @Test
  void consultsTheStrategyOverThePoolWhenSmartRoutingIsOn() {
    ModelRouter router =
        new ModelRouter(
            new ModelRoutingProperties(Map.of("fast", "openai/gpt-4o-mini")),
            new SmartRoutingProperties(true, Map.of("fast", List.of("a", "b"))),
            PICK_LAST);

    assertThat(router.resolve("fast", ctx())).isEqualTo("b");
  }

  @Test
  void fallsBackToTheTableForAnAliasWithoutAPool() {
    ModelRouter router =
        new ModelRouter(
            new ModelRoutingProperties(Map.of("smart", "openai/gpt-4o")),
            new SmartRoutingProperties(true, Map.of("fast", List.of("a", "b"))),
            PICK_LAST);

    assertThat(router.resolve("smart", ctx())).isEqualTo("openai/gpt-4o");
  }

  @Test
  void rejectsAnUnknownAlias() {
    ModelRouter router =
        new ModelRouter(
            new ModelRoutingProperties(Map.of("fast", "openai/gpt-4o-mini")),
            new SmartRoutingProperties(false, Map.of()),
            PICK_LAST);

    assertThatThrownBy(() -> router.resolve("nope", ctx()))
        .isInstanceOf(ModelNotAllowedException.class);
  }
}

package com.gatewise.gateway.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for alias-to-provider-model routing. */
class ModelRouterTest {

  private final ModelRouter router =
      new ModelRouter(
          new ModelRoutingProperties(
              Map.of("fast", "openai/gpt-4o-mini", "smart", "openai/gpt-4o")),
          new SmartRoutingProperties(false, Map.of()),
          (candidates, context) -> candidates.get(0));

  @Test
  void resolvesKnownAlias() { // T16
    assertThat(router.resolve("fast")).isEqualTo("openai/gpt-4o-mini");
    assertThat(router.resolve("smart")).isEqualTo("openai/gpt-4o");
  }

  @Test
  void rejectsUnknownAliasWithAllowedList() { // T17
    assertThatThrownBy(() -> router.resolve("does-not-exist"))
        .isInstanceOf(ModelNotAllowedException.class)
        .hasMessageContaining("does-not-exist")
        .hasMessageContaining("fast")
        .hasMessageContaining("smart");
  }
}

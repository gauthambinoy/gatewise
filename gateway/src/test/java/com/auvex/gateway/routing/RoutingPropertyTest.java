package com.auvex.gateway.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based tests for {@link ModelRouter}: for any generated routing table every configured
 * alias resolves to its exact provider model, and any alias not in the table is rejected.
 */
class RoutingPropertyTest {

  private static ModelRouter routerFor(Map<String, String> table) {
    return new ModelRouter(
        new ModelRoutingProperties(table),
        new SmartRoutingProperties(false, Map.of()),
        (candidates, context) -> candidates.get(0));
  }

  @Property
  void everyConfiguredAliasResolvesToItsProviderModel(
      @ForAll("routingTables") Map<String, String> table) {
    ModelRouter router = routerFor(table);
    for (Map.Entry<String, String> entry : table.entrySet()) {
      assertThat(router.resolve(entry.getKey())).isEqualTo(entry.getValue());
    }
  }

  @Property
  void unknownAliasIsRejected(
      @ForAll("routingTables") Map<String, String> table, @ForAll("aliasNames") String unknown) {
    Assume.that(!table.containsKey(unknown));
    ModelRouter router = routerFor(table);
    assertThatThrownBy(() -> router.resolve(unknown))
        .isInstanceOf(ModelNotAllowedException.class)
        .hasMessageContaining(unknown);
  }

  // --- Generators ---------------------------------------------------------------------------

  @Provide
  Arbitrary<Map<String, String>> routingTables() {
    Arbitrary<String> model =
        Combinators.combine(
                Arbitraries.strings().withCharRange('a', 'z').ofMinLength(2).ofMaxLength(8),
                Arbitraries.strings().withCharRange('a', 'z').ofMinLength(2).ofMaxLength(8))
            .as((provider, name) -> provider + "/" + name);
    return Arbitraries.maps(aliasNames(), model).ofMinSize(1).ofMaxSize(12);
  }

  @Provide
  Arbitrary<String> aliasNames() {
    return Arbitraries.strings()
        .withChars("abcdefghijklmnopqrstuvwxyz0123456789-_")
        .ofMinLength(1)
        .ofMaxLength(12);
  }
}

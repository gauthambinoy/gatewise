package com.gatewise.gateway.plugins;

import static org.assertj.core.api.Assertions.assertThat;

import com.gatewise.gateway.injection.InjectionFinding;
import com.gatewise.gateway.injection.InjectionRule;
import com.gatewise.gateway.moderation.ModerationFinding;
import com.gatewise.gateway.moderation.ModerationRule;
import com.gatewise.gateway.plugins.PluginProperties.CustomDetectorSpec;
import com.gatewise.gateway.plugins.PluginsController.PluginCatalog;
import com.gatewise.gateway.redaction.Detector;
import com.gatewise.gateway.redaction.EmailDetector;
import com.gatewise.gateway.routing.RoutingContext;
import com.gatewise.gateway.routing.RoutingStrategy;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Pure-JUnit tests for {@link PluginsController}: the read-only governance-plugin catalog. */
class PluginsControllerTest {

  @Test
  void catalogListsCategoriesRulesAndCustomDetectors() {
    PluginProperties props =
        new PluginProperties(true, List.of(new CustomDetectorSpec("employee-id", "EMP-\\d{4}", 5)));
    List<Detector> detectors = List.of(new EmailDetector(), new CustomDetectors(props));
    PluginsController controller =
        new PluginsController(
            detectors,
            List.of(new FakeInjectionRule()),
            List.of(new FakeModerationRule()),
            List.of(new FakeRoutingStrategy()),
            props);

    PluginCatalog catalog = controller.plugins();

    assertThat(catalog.customDetectorsEnabled()).isTrue();
    assertThat(catalog.redactionDetectors()).contains("EMAIL", "CUSTOM");
    assertThat(catalog.injectionRules()).containsExactly("FakeInjectionRule");
    assertThat(catalog.moderationRules()).containsExactly("FakeModerationRule");
    assertThat(catalog.routingStrategies()).containsExactly("FakeRoutingStrategy");
    assertThat(catalog.customDetectors())
        .singleElement()
        .satisfies(
            d -> {
              assertThat(d.name()).isEqualTo("employee-id");
              assertThat(d.pattern()).isEqualTo("EMP-\\d{4}");
            });
  }

  @Test
  void catalogIsEmptyAndDisabledWithNoCustomDetectors() {
    PluginProperties props = new PluginProperties(false, List.of());
    PluginsController controller =
        new PluginsController(List.of(new EmailDetector()), List.of(), List.of(), List.of(), props);

    PluginCatalog catalog = controller.plugins();

    assertThat(catalog.customDetectorsEnabled()).isFalse();
    assertThat(catalog.customDetectors()).isEmpty();
    assertThat(catalog.redactionDetectors()).containsExactly("EMAIL");
    assertThat(catalog.injectionRules()).isEmpty();
    assertThat(catalog.moderationRules()).isEmpty();
    assertThat(catalog.routingStrategies()).isEmpty();
  }

  /** A no-op injection rule whose simple name is asserted in the catalog. */
  static final class FakeInjectionRule implements InjectionRule {
    @Override
    public Optional<InjectionFinding> check(String text) {
      return Optional.empty();
    }
  }

  /** A no-op moderation rule whose simple name is asserted in the catalog. */
  static final class FakeModerationRule implements ModerationRule {
    @Override
    public Optional<ModerationFinding> check(String text) {
      return Optional.empty();
    }
  }

  /** A trivial routing strategy whose simple name is asserted in the catalog. */
  static final class FakeRoutingStrategy implements RoutingStrategy {
    @Override
    public String select(List<String> candidates, RoutingContext context) {
      return candidates.get(0);
    }
  }
}

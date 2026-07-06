package com.gatewise.gateway.plugins;

import com.gatewise.gateway.injection.InjectionRule;
import com.gatewise.gateway.moderation.ModerationRule;
import com.gatewise.gateway.redaction.Detector;
import com.gatewise.gateway.redaction.PiiType;
import com.gatewise.gateway.routing.RoutingStrategy;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catalog of the governance plugins installed in this gateway.
 *
 * <p>{@code GET /v1/plugins} returns read-only, tenant-agnostic metadata: the redaction detector
 * categories, the injection / moderation rule and routing strategy implementations that are wired
 * in, and the operator-registered custom detectors (name and pattern only — never a matched value).
 * Because every plugin SPI is a Spring bean collection, this list reflects exactly what is loaded.
 */
@RestController
@RequestMapping("/v1")
public class PluginsController {

  private final List<Detector> detectors;
  private final List<InjectionRule> injectionRules;
  private final List<ModerationRule> moderationRules;
  private final List<RoutingStrategy> routingStrategies;
  private final PluginProperties properties;

  /**
   * Injects every installed plugin SPI bean plus the plugin configuration.
   *
   * @param detectors all redaction detector beans
   * @param injectionRules all prompt-injection rule beans
   * @param moderationRules all content-moderation rule beans
   * @param routingStrategies all model-routing strategy beans
   * @param properties the plugin marketplace configuration
   */
  public PluginsController(
      List<Detector> detectors,
      List<InjectionRule> injectionRules,
      List<ModerationRule> moderationRules,
      List<RoutingStrategy> routingStrategies,
      PluginProperties properties) {
    this.detectors = List.copyOf(detectors);
    this.injectionRules = List.copyOf(injectionRules);
    this.moderationRules = List.copyOf(moderationRules);
    this.routingStrategies = List.copyOf(routingStrategies);
    this.properties = properties;
  }

  /** Returns the installed-governance-plugins catalog. */
  @GetMapping("/plugins")
  public PluginCatalog plugins() {
    List<String> detectorCategories =
        detectors.stream().map(Detector::type).map(PiiType::name).distinct().sorted().toList();
    List<CustomDetectorView> custom =
        properties.compiledDetectors().stream()
            .map(d -> new CustomDetectorView(d.name(), d.pattern().pattern()))
            .toList();
    return new PluginCatalog(
        properties.enabled(),
        detectorCategories,
        simpleNames(injectionRules),
        simpleNames(moderationRules),
        simpleNames(routingStrategies),
        custom);
  }

  // Distinct, sorted simple class names of a bean collection — stable metadata, no internals.
  private static List<String> simpleNames(List<?> beans) {
    return beans.stream().map(b -> b.getClass().getSimpleName()).distinct().sorted().toList();
  }

  /**
   * The installed-governance-plugins catalog (read-only metadata; carries no secrets).
   *
   * @param customDetectorsEnabled whether operator-registered custom detectors are active
   * @param redactionDetectors the distinct redaction detector categories that are installed
   * @param injectionRules the installed prompt-injection rule class names
   * @param moderationRules the installed content-moderation rule class names
   * @param routingStrategies the installed model-routing strategy class names
   * @param customDetectors the configured custom detectors (name and pattern only)
   */
  public record PluginCatalog(
      boolean customDetectorsEnabled,
      List<String> redactionDetectors,
      List<String> injectionRules,
      List<String> moderationRules,
      List<String> routingStrategies,
      List<CustomDetectorView> customDetectors) {}

  /**
   * One operator-registered custom detector as exposed in the catalog.
   *
   * @param name the detector's label
   * @param pattern the detector's regular expression (the pattern, never a matched value)
   */
  public record CustomDetectorView(String name, String pattern) {}
}

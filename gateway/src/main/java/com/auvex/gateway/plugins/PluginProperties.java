package com.auvex.gateway.plugins;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Plugin marketplace configuration: operator-registered custom regex redaction detectors.
 *
 * <p>When {@code enabled} (off by default) every spec in {@code detectors} adds an extra
 * regex-based PII detector to the redaction pipeline without a code change. Each spec is compiled
 * once at startup; a spec with a blank name or pattern, or an invalid regex, is skipped with a
 * warning rather than failing the boot, so one bad entry can never take the gateway down. With no
 * detectors configured (the default) the feature is inert and redaction behaviour is unchanged.
 *
 * @param enabled master switch for the custom detectors; the {@code /v1/plugins} catalog is always
 *     available regardless
 * @param detectors the operator-registered custom regex detectors, in priority/declaration order
 */
@ConfigurationProperties(prefix = "auvex.plugins")
public record PluginProperties(boolean enabled, List<CustomDetectorSpec> detectors) {

  private static final Logger LOG = LoggerFactory.getLogger(PluginProperties.class);

  /** Default overlap tie-break weight for a custom detector that doesn't declare one. */
  public static final int DEFAULT_PRIORITY = 100;

  /** Defaults a null detector list to an empty, immutable one. */
  public PluginProperties {
    detectors = detectors == null ? List.of() : List.copyOf(detectors);
  }

  /**
   * One operator-registered custom detector.
   *
   * @param name a short label for the detector, surfaced in the catalog and logs
   * @param pattern the Java regular expression whose every (non-empty) match is redacted
   * @param priority overlap tie-break weight, lower wins; {@link #DEFAULT_PRIORITY} when null
   */
  public record CustomDetectorSpec(String name, String pattern, Integer priority) {}

  /**
   * A validated, ready-to-run custom detector: its label, compiled pattern and effective priority.
   *
   * @param name the detector's label
   * @param pattern the compiled regular expression
   * @param priority the effective overlap tie-break weight
   */
  public record CompiledDetector(String name, Pattern pattern, int priority) {}

  /**
   * Compiles the configured specs into ready-to-run detectors, skipping any with a blank name or
   * pattern, or an invalid regex (each skip is logged once). This does not consult {@link
   * #enabled()} — activation is the caller's decision — so it always reflects what is configured.
   *
   * @return the valid compiled custom detectors, in declaration order; never {@code null}
   */
  public List<CompiledDetector> compiledDetectors() {
    List<CompiledDetector> compiled = new ArrayList<>();
    for (CustomDetectorSpec spec : detectors) {
      if (spec == null || spec.name() == null || spec.name().isBlank()) {
        LOG.warn("Skipping custom detector with no name");
        continue;
      }
      String regex = spec.pattern();
      if (regex == null || regex.isBlank()) {
        LOG.warn("Skipping custom detector '{}' with no pattern", spec.name());
        continue;
      }
      Integer declared = spec.priority();
      int priority = declared == null ? DEFAULT_PRIORITY : declared;
      try {
        compiled.add(new CompiledDetector(spec.name(), Pattern.compile(regex), priority));
      } catch (PatternSyntaxException e) {
        LOG.warn(
            "Skipping custom detector '{}' with invalid regex: {}", spec.name(), e.getMessage());
      }
    }
    return compiled;
  }
}

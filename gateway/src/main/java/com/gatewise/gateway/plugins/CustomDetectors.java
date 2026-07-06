package com.gatewise.gateway.plugins;

import com.gatewise.gateway.plugins.PluginProperties.CompiledDetector;
import com.gatewise.gateway.redaction.Detector;
import com.gatewise.gateway.redaction.Match;
import com.gatewise.gateway.redaction.PiiType;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import org.springframework.stereotype.Component;

/**
 * Redaction detector that runs every operator-registered custom regex (see {@link
 * PluginProperties}). Spring injects this as a {@link Detector} bean, so its hits flow through the
 * normal {@link com.gatewise.gateway.redaction.RedactionEngine} overlap resolution and masking just
 * like a built-in detector.
 *
 * <p>The configured patterns are compiled once in the constructor. The detector is active only when
 * {@code gatewise.plugins.enabled} is on; otherwise — and whenever no valid custom detector is
 * configured — it finds nothing, so it is inert and drop-in safe by default.
 */
@Component
public class CustomDetectors implements Detector {

  private final List<CompiledDetector> detectors;

  /**
   * Compiles the active custom detectors once from {@code properties}.
   *
   * @param properties the plugin marketplace configuration
   */
  public CustomDetectors(PluginProperties properties) {
    this.detectors = properties.enabled() ? List.copyOf(properties.compiledDetectors()) : List.of();
  }

  @Override
  public PiiType type() {
    return PiiType.CUSTOM;
  }

  @Override
  public int priority() {
    return PluginProperties.DEFAULT_PRIORITY;
  }

  @Override
  public List<Match> detect(CharSequence text) {
    if (detectors.isEmpty() || text == null) {
      return List.of();
    }
    List<Match> matches = new ArrayList<>();
    for (CompiledDetector detector : detectors) {
      Matcher matcher = detector.pattern().matcher(text);
      while (matcher.find()) {
        if (matcher.end() > matcher.start()) {
          matches.add(
              new Match(PiiType.CUSTOM, matcher.start(), matcher.end(), detector.priority()));
        }
      }
    }
    return matches;
  }
}

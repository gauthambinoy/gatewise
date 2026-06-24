package com.auvex.gateway.moderation;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Runs every {@link ModerationRule} over a piece of text and collects the safety findings. */
@Component
public class ContentModerationScanner {

  private final List<ModerationRule> rules;

  public ContentModerationScanner(List<ModerationRule> rules) {
    this.rules = List.copyOf(rules);
  }

  /** All rule findings for the given text (empty when nothing matches). */
  public List<ModerationFinding> scan(String text) {
    return rules.stream()
        .map(rule -> rule.check(text))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .toList();
  }

  /** The distinct safety categories detected in the text, in wire form (e.g. {@code self-harm}). */
  public List<String> categories(String text) {
    return scan(text).stream().map(f -> f.category().value()).distinct().toList();
  }
}

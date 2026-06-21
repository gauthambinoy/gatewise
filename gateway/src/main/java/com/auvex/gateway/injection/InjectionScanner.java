package com.auvex.gateway.injection;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Runs every {@link InjectionRule} over a prompt and collects the findings. */
@Component
public class InjectionScanner {

  private final List<InjectionRule> rules;

  public InjectionScanner(List<InjectionRule> rules) {
    this.rules = List.copyOf(rules);
  }

  /** All rule findings for the given text (empty when nothing matches). */
  public List<InjectionFinding> scan(String text) {
    return rules.stream()
        .map(rule -> rule.check(text))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .toList();
  }
}

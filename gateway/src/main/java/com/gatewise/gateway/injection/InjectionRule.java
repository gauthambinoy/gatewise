package com.gatewise.gateway.injection;

import java.util.Optional;

/**
 * One prompt-injection / jailbreak heuristic. Spring injects every {@link InjectionRule} bean into
 * the {@link InjectionScanner}, so adding a rule is just adding an {@code @Component} class.
 */
public interface InjectionRule {

  /** Returns a finding if this rule matches the text, or empty if it doesn't. */
  Optional<InjectionFinding> check(String text);
}

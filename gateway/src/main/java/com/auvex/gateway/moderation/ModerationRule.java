package com.auvex.gateway.moderation;

import java.util.Optional;

/**
 * One content-safety heuristic. Spring injects every {@link ModerationRule} bean into the {@link
 * ContentModerationScanner}, so adding a category check is just adding an {@code @Component}.
 *
 * <p>These are deliberately conservative, intent-focused heuristics — a fast, dependency-free first
 * pass meant to be augmented (not replaced) by a model classifier in a high-assurance deployment.
 */
public interface ModerationRule {

  /** Returns a finding if this rule matches the text, or empty if it doesn't. */
  Optional<ModerationFinding> check(String text);
}

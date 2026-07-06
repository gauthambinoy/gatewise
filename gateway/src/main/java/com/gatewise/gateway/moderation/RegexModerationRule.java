package com.gatewise.gateway.moderation;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base for moderation rules that fire when any of a category's (case-insensitive) regexes match.
 */
public abstract class RegexModerationRule implements ModerationRule {

  private final ModerationCategory category;
  private final Pattern[] patterns;

  protected RegexModerationRule(ModerationCategory category, Pattern... patterns) {
    this.category = category;
    this.patterns = patterns.clone();
  }

  @Override
  public Optional<ModerationFinding> check(String text) {
    if (text == null || text.isBlank()) {
      return Optional.empty();
    }
    for (Pattern pattern : patterns) {
      Matcher matcher = pattern.matcher(text);
      if (matcher.find()) {
        return Optional.of(new ModerationFinding(category, matcher.group()));
      }
    }
    return Optional.empty();
  }

  /** Compiles a case-insensitive pattern (the common case for these lexical heuristics). */
  protected static Pattern ci(String regex) {
    return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
  }
}

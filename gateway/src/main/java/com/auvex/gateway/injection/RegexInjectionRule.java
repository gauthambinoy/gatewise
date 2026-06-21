package com.auvex.gateway.injection;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Base for injection rules that fire when a (case-insensitive) regex matches. */
public abstract class RegexInjectionRule implements InjectionRule {

  private final String name;
  private final String category;
  private final Pattern pattern;

  protected RegexInjectionRule(String name, String category, Pattern pattern) {
    this.name = name;
    this.category = category;
    this.pattern = pattern;
  }

  @Override
  public Optional<InjectionFinding> check(String text) {
    if (text == null) {
      return Optional.empty();
    }
    Matcher matcher = pattern.matcher(text);
    if (matcher.find()) {
      return Optional.of(new InjectionFinding(name, category, matcher.group()));
    }
    return Optional.empty();
  }
}

package com.gatewise.gateway.redaction;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base for detectors that find candidates with a regex and optionally validate each one.
 *
 * <p>The regex is the cheap broad net; subclasses add precision by overriding {@link
 * #accept(Matcher)} (e.g. a Luhn or mod-97 check). Subclasses that capture the value in a group
 * (rather than the whole match) override {@link #maskGroup()}.
 */
public abstract class RegexDetector implements Detector {

  private final PiiType type;
  private final int priority;
  private final Pattern pattern;

  protected RegexDetector(PiiType type, int priority, Pattern pattern) {
    this.type = type;
    this.priority = priority;
    this.pattern = pattern;
  }

  @Override
  public PiiType type() {
    return type;
  }

  @Override
  public int priority() {
    return priority;
  }

  @Override
  public List<Match> detect(CharSequence text) {
    List<Match> matches = new ArrayList<>();
    Matcher matcher = pattern.matcher(text);
    int group = maskGroup();
    while (matcher.find()) {
      if (accept(matcher)) {
        matches.add(new Match(type, matcher.start(group), matcher.end(group), priority));
      }
    }
    return matches;
  }

  /** Which capture group to mask; the whole match (group 0) by default. */
  protected int maskGroup() {
    return 0;
  }

  /** Hook to reject a regex hit (e.g. a failed checksum); accepts everything by default. */
  protected boolean accept(Matcher matcher) {
    return true;
  }
}

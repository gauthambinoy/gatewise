package com.auvex.gateway.redaction;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Runs every detector over a piece of text and masks what they find.
 *
 * <p>Spring injects all {@link Detector} beans, so adding a detector is just adding a class. The
 * masked text is rebuilt in a single linear pass, so a body with no sensitive data is returned
 * untouched (no allocation) and a large body stays O(n).
 */
@Component
public class RedactionEngine {

  private final List<Detector> detectors;
  private final Masker masker;
  private final OverlapResolver resolver = new OverlapResolver();

  public RedactionEngine(List<Detector> detectors, Masker masker) {
    this.detectors = List.copyOf(detectors);
    this.masker = masker;
  }

  /**
   * Masks all detectable sensitive data in {@code text}, returning the masked text and findings.
   */
  public RedactionResult redact(String text) {
    if (text == null) {
      throw new IllegalArgumentException("text must not be null");
    }
    if (text.isEmpty()) {
      return new RedactionResult(text, List.of(), false);
    }

    List<Match> all = new ArrayList<>();
    for (Detector detector : detectors) {
      all.addAll(detector.detect(text));
    }
    if (all.isEmpty()) {
      return new RedactionResult(text, List.of(), false);
    }

    List<Match> kept = resolver.resolve(all);
    StringBuilder out = new StringBuilder(text.length());
    int cursor = 0;
    for (Match match : kept) {
      out.append(text, cursor, match.start());
      out.append(masker.tokenFor(match.type()));
      cursor = match.end();
    }
    out.append(text, cursor, text.length());
    return new RedactionResult(out.toString(), kept, true);
  }
}

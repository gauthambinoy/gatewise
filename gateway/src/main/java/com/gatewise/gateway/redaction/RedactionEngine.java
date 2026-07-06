package com.gatewise.gateway.redaction;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    List<Match> kept = text.isEmpty() ? List.of() : detect(text);
    if (kept.isEmpty()) {
      return new RedactionResult(text, List.of(), false);
    }

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

  /**
   * Like {@link #redact} but with a reversible, per-value token for each finding, plus the vault to
   * restore them. The token is derived from the value, so equal values map to the same token (the
   * masked text — and therefore the cache key — stays stable) and different values never collide.
   */
  public ReversibleRedaction redactReversibly(String text) {
    if (text == null) {
      throw new IllegalArgumentException("text must not be null");
    }
    List<Match> kept = text.isEmpty() ? List.of() : detect(text);
    if (kept.isEmpty()) {
      return new ReversibleRedaction(text, Map.of(), List.of());
    }

    Map<String, String> vault = new LinkedHashMap<>();
    StringBuilder out = new StringBuilder(text.length());
    int cursor = 0;
    for (Match match : kept) {
      out.append(text, cursor, match.start());
      String value = text.substring(match.start(), match.end());
      String token = reversibleToken(match.type(), value);
      vault.put(token, value);
      out.append(token);
      cursor = match.end();
    }
    out.append(text, cursor, text.length());
    return new ReversibleRedaction(out.toString(), vault, kept);
  }

  // Detect across all detectors and resolve overlaps; empty when nothing is found.
  private List<Match> detect(String text) {
    List<Match> all = new ArrayList<>();
    for (Detector detector : detectors) {
      all.addAll(detector.detect(text));
    }
    return all.isEmpty() ? List.of() : resolver.resolve(all);
  }

  // A unique, value-derived placeholder, e.g. ⟦EMAIL_1a2b3c4d⟧.
  private static String reversibleToken(PiiType type, String value) {
    return "⟦" + type.name() + "_" + shortHash(value) + "⟧";
  }

  private static String shortHash(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest, 0, 4);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required", e);
    }
  }
}

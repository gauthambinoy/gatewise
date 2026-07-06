package com.gatewise.gateway.redaction;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** The outcome of a redaction pass: the masked text plus what was found. */
public record RedactionResult(String masked, List<Match> matches, boolean changed) {

  public RedactionResult {
    matches = List.copyOf(matches);
  }

  /** How many spans were masked. */
  public int count() {
    return matches.size();
  }

  /** A breakdown of how many of each category were masked. */
  public Map<PiiType, Long> countsByType() {
    return matches.stream().collect(Collectors.groupingBy(Match::type, Collectors.counting()));
  }
}

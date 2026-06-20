package com.auvex.gateway.redaction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Collapses overlapping matches into a clean, ordered, non-overlapping list.
 *
 * <p>Sort by (start asc, length desc, priority asc), then sweep left-to-right keeping a running
 * {@code lastEnd} and accepting a match only when it starts at or after it. The effect: at each
 * position the longest match wins, ties go to priority, and nothing is ever masked twice.
 */
public final class OverlapResolver {

  /** Returns the kept matches, sorted by start position. */
  public List<Match> resolve(List<Match> matches) {
    List<Match> sorted = new ArrayList<>(matches);
    sorted.sort(
        Comparator.comparingInt(Match::start)
            .thenComparing(Comparator.comparingInt(Match::length).reversed())
            .thenComparingInt(Match::priority));

    List<Match> kept = new ArrayList<>();
    int lastEnd = -1;
    for (Match match : sorted) {
      if (match.start() >= lastEnd) {
        kept.add(match);
        lastEnd = match.end();
      }
    }
    return kept;
  }
}

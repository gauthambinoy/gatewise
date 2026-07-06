package com.gatewise.gateway.redaction;

/**
 * A half-open span {@code [start, end)} in the source text that a detector claims.
 *
 * <p>It deliberately does NOT carry the matched value — keeping the raw secret out of any object we
 * might accidentally log. {@code priority} (lower wins) lets the overlap resolver break ties
 * without knowing detector internals.
 */
public record Match(PiiType type, int start, int end, int priority) {

  public Match {
    if (start < 0 || end < start) {
      throw new IllegalArgumentException("Invalid span: [" + start + ", " + end + ")");
    }
  }

  /** Length of the matched span. */
  public int length() {
    return end - start;
  }
}

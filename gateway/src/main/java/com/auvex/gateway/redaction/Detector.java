package com.auvex.gateway.redaction;

import java.util.List;

/** Finds spans of one category of sensitive data in a piece of text. Stateless and thread-safe. */
public interface Detector {

  /** The category this detector finds. */
  PiiType type();

  /** Tie-break weight when two matches overlap; lower wins. */
  int priority();

  /** Returns every (internally non-overlapping) match in {@code text}. */
  List<Match> detect(CharSequence text);
}

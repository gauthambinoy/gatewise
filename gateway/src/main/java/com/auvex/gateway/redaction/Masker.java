package com.auvex.gateway.redaction;

/** Produces the placeholder token that replaces a matched span. */
public interface Masker {

  /** The literal replacement token for a given category. */
  String tokenFor(PiiType type);
}

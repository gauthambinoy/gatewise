package com.auvex.gateway.redaction;

import java.util.List;
import java.util.Map;

/**
 * Reversible redaction: the masked text plus the vault that maps each placeholder token back to its
 * original value, so the response can be restored for the caller while the provider only ever sees
 * the tokens.
 *
 * @param masked the text with each sensitive value replaced by a unique token
 * @param vault token → original value (deterministic per value, so equal values share a token)
 * @param matches what was found
 */
public record ReversibleRedaction(String masked, Map<String, String> vault, List<Match> matches) {

  public boolean changed() {
    return !matches.isEmpty();
  }
}

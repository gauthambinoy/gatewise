package com.gatewise.gateway.redaction;

import java.util.List;

/**
 * The outcome of redacting a provider response: the masked reply text (for the audit log), what was
 * found, and the response body (rewritten with masked content if anything was redacted).
 */
public record ResponseRedaction(String redactedText, List<Match> matches, String maskedBody) {

  public ResponseRedaction {
    matches = List.copyOf(matches);
  }

  /** No redaction performed — the body is returned unchanged. */
  public static ResponseRedaction none(String body) {
    return new ResponseRedaction("", List.of(), body);
  }
}

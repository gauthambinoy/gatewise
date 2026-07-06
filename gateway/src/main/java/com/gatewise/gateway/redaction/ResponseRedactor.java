package com.gatewise.gateway.redaction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Redacts PII out of a provider's buffered chat-completions response — for the audit record, and
 * (when enforcement is on) for what the caller receives. A model can echo back or invent sensitive
 * data, so the response needs masking just like the prompt does.
 */
@Component
public class ResponseRedactor {

  private final RedactionEngine engine;
  private final ObjectMapper json;

  public ResponseRedactor(RedactionEngine engine, ObjectMapper json) {
    this.engine = engine;
    this.json = json;
  }

  /** Masks sensitive data in the assistant's reply; returns the masked text, findings and body. */
  public ResponseRedaction redact(String responseBody) {
    if (responseBody == null || responseBody.isBlank()) {
      return ResponseRedaction.none(responseBody);
    }
    JsonNode root;
    try {
      root = json.readTree(responseBody);
    } catch (JsonProcessingException e) {
      return ResponseRedaction.none(responseBody); // not JSON (e.g. an error body) — leave it as-is
    }
    JsonNode choices = root.path("choices");
    if (!choices.isArray() || choices.isEmpty()) {
      return ResponseRedaction.none(responseBody);
    }

    StringBuilder text = new StringBuilder();
    List<Match> matches = new ArrayList<>();
    boolean changed = false;
    for (JsonNode choice : choices) {
      JsonNode message = choice.path("message");
      JsonNode content = message.path("content");
      if (!content.isTextual()) {
        continue;
      }
      RedactionResult result = engine.redact(content.asText());
      if (text.length() > 0) {
        text.append('\n');
      }
      text.append(result.masked());
      matches.addAll(result.matches());
      if (result.changed() && message.isObject()) {
        ((ObjectNode) message).put("content", result.masked());
        changed = true;
      }
    }
    String maskedBody = changed ? root.toString() : responseBody;
    return new ResponseRedaction(text.toString(), matches, maskedBody);
  }
}

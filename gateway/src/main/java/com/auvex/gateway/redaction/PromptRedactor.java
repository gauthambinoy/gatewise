package com.auvex.gateway.redaction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Redacts the text content of a chat-completions request in place, before it is forwarded.
 *
 * <p>Each message's textual {@code content} is run through the redaction engine and replaced with
 * the masked version. Non-text content (e.g. multimodal arrays) is left untouched for now. The
 * combined findings are returned so the audit log can later record what was masked.
 */
@Component
public class PromptRedactor {

  private final RedactionEngine engine;

  public PromptRedactor(RedactionEngine engine) {
    this.engine = engine;
  }

  /**
   * Masks sensitive data in every message's content, mutating {@code body}; returns the findings.
   */
  public List<Match> redactInPlace(JsonNode body) {
    List<Match> found = new ArrayList<>();
    JsonNode messages = body.get("messages");
    if (messages == null || !messages.isArray()) {
      return found;
    }
    for (JsonNode message : messages) {
      if (!message.isObject()) {
        continue;
      }
      JsonNode content = message.get("content");
      if (content != null && content.isTextual()) {
        RedactionResult result = engine.redact(content.asText());
        if (result.changed()) {
          ((ObjectNode) message).put("content", result.masked());
          found.addAll(result.matches());
        }
      }
    }
    return found;
  }
}

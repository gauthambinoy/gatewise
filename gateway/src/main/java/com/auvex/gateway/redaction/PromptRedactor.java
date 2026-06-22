package com.auvex.gateway.redaction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Redacts the sensitive text of a chat-completions request in place, before it is forwarded.
 *
 * <p>It covers every place a prompt can carry text: each message's {@code content} (a plain string
 * or an array of {@code {type:text}} parts, for multimodal requests) and each tool call's {@code
 * function.arguments} (the JSON a function-calling model is asked to pass on) — so PII can't slip
 * past in a non-content field. The combined findings are returned so the audit log can record what
 * was masked.
 */
@Component
public class PromptRedactor {

  private final RedactionEngine engine;

  public PromptRedactor(RedactionEngine engine) {
    this.engine = engine;
  }

  /**
   * Masks sensitive data in every redactable field, mutating {@code body}; returns the findings.
   */
  public List<Match> redactInPlace(JsonNode body) {
    List<Match> found = new ArrayList<>();
    forEachRedactableText(
        body,
        (node, field, text) -> {
          RedactionResult result = engine.redact(text);
          if (result.changed()) {
            node.put(field, result.masked());
            found.addAll(result.matches());
          }
        });
    return found;
  }

  /**
   * Like {@link #redactInPlace} but reversibly: every redactable field is masked with per-value
   * tokens, and the combined vault is returned so the caller can restore the response. (The
   * result's {@code masked} text is unused at the body level — the fields are mutated in place.)
   */
  public ReversibleRedaction redactReversiblyInPlace(JsonNode body) {
    List<Match> found = new ArrayList<>();
    Map<String, String> vault = new LinkedHashMap<>();
    forEachRedactableText(
        body,
        (node, field, text) -> {
          ReversibleRedaction result = engine.redactReversibly(text);
          if (result.changed()) {
            node.put(field, result.masked());
            found.addAll(result.matches());
            vault.putAll(result.vault());
          }
        });
    return new ReversibleRedaction("", vault, found);
  }

  // Visit every redactable text location in the request, so both paths share one traversal.
  private void forEachRedactableText(JsonNode body, TextVisitor visitor) {
    JsonNode messages = body.get("messages");
    if (messages == null || !messages.isArray()) {
      return;
    }
    for (JsonNode message : messages) {
      if (!message.isObject()) {
        continue;
      }
      JsonNode content = message.get("content");
      if (content != null && content.isTextual()) {
        visitor.visit((ObjectNode) message, "content", content.asText());
      } else if (content != null && content.isArray()) {
        for (JsonNode part : content) {
          if (isTextField(part, "text")) {
            visitor.visit((ObjectNode) part, "text", part.get("text").asText());
          }
        }
      }
      JsonNode toolCalls = message.get("tool_calls");
      if (toolCalls != null && toolCalls.isArray()) {
        for (JsonNode call : toolCalls) {
          JsonNode function = call.get("function");
          if (isTextField(function, "arguments")) {
            visitor.visit((ObjectNode) function, "arguments", function.get("arguments").asText());
          }
        }
      }
    }
  }

  private static boolean isTextField(JsonNode node, String field) {
    return node != null
        && node.isObject()
        && node.get(field) != null
        && node.get(field).isTextual();
  }

  @FunctionalInterface
  private interface TextVisitor {
    void visit(ObjectNode node, String field, String text);
  }
}

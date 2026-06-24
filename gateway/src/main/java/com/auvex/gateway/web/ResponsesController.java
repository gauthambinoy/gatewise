package com.auvex.gateway.web;

import com.auvex.gateway.audit.AuditEntry;
import com.auvex.gateway.audit.AuditSink;
import com.auvex.gateway.audit.Verdict;
import com.auvex.gateway.auth.TenantContext;
import com.auvex.gateway.proxy.CachedResponse;
import com.auvex.gateway.proxy.UpstreamUnavailableException;
import com.auvex.gateway.redaction.RedactionEngine;
import com.auvex.gateway.redaction.RedactionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * OpenAI's newer Responses API ({@code POST /v1/responses}), governed like the chat path.
 *
 * <p>The Responses API carries the prompt in an {@code input} field that is either a plain string or
 * an array of items whose {@code content} is a string or an array of {@code {type, text}} parts —
 * unlike chat completions, which uses {@code messages}. We redact every text location in {@code
 * input} before it leaves, record the call, forward it to the provider's {@code /responses}
 * endpoint, then screen the returned text for PII so the audit log captures both sides.
 */
@RestController
@RequestMapping("/v1")
public class ResponsesController {

  private final RestClient openRouter;
  private final RedactionEngine redaction;
  private final AuditSink auditSink;
  private final ObjectMapper objectMapper;

  public ResponsesController(
      RestClient openRouterRestClient,
      RedactionEngine redaction,
      AuditSink auditSink,
      ObjectMapper objectMapper) {
    this.openRouter = openRouterRestClient;
    this.redaction = redaction;
    this.auditSink = auditSink;
    this.objectMapper = objectMapper;
  }

  /** Redacts the input, records the call, forwards it, and screens the response. */
  @PostMapping("/responses")
  public ResponseEntity<String> create(@RequestBody JsonNode body) throws IOException {
    validate(body);

    List<String> maskedTexts = new ArrayList<>();
    boolean changed = redactInput((ObjectNode) body, maskedTexts);

    UUID tenantId = TenantContext.require().tenantId();
    String actor =
        body.hasNonNull("user") && body.get("user").isTextual()
            ? body.get("user").asText()
            : "unknown";

    byte[] forwarded = objectMapper.writeValueAsBytes(body);
    CachedResponse response = forward(forwarded);

    String responseText = screenResponse(response.body());
    Verdict verdict = changed ? Verdict.REDACTED : Verdict.ALLOWED;
    auditSink.record(
        new AuditEntry(
            tenantId,
            UUID.randomUUID(),
            actor,
            body.get("model").asText(),
            verdict,
            String.join("\n", maskedTexts),
            responseText.isBlank() ? null : responseText,
            Instant.now()));

    return ResponseEntity.status(response.status())
        .contentType(MediaType.APPLICATION_JSON)
        .body(response.body());
  }

  /**
   * Masks sensitive data in the {@code input} in place, collecting the masked text for the audit
   * record. Returns whether anything was masked. Handles a plain-string input and an array whose
   * items carry a string {@code content} or an array of {@code {text}} parts.
   */
  private boolean redactInput(ObjectNode body, List<String> maskedTexts) {
    JsonNode input = body.get("input");
    boolean changed = false;
    if (input.isTextual()) {
      RedactionResult result = redaction.redact(input.asText());
      maskedTexts.add(result.masked());
      if (result.changed()) {
        body.put("input", result.masked());
      }
      return result.changed();
    }
    for (JsonNode item : (ArrayNode) input) {
      if (!item.isObject()) {
        continue;
      }
      JsonNode content = item.get("content");
      if (content != null && content.isTextual()) {
        RedactionResult result = redaction.redact(content.asText());
        maskedTexts.add(result.masked());
        if (result.changed()) {
          ((ObjectNode) item).put("content", result.masked());
          changed = true;
        }
      } else if (content != null && content.isArray()) {
        for (JsonNode part : content) {
          if (part.isObject() && part.hasNonNull("text") && part.get("text").isTextual()) {
            RedactionResult result = redaction.redact(part.get("text").asText());
            maskedTexts.add(result.masked());
            if (result.changed()) {
              ((ObjectNode) part).put("text", result.masked());
              changed = true;
            }
          }
        }
      }
    }
    return changed;
  }

  /** Pulls the assistant's text out of a Responses-shaped body and masks it for the audit log. */
  private String screenResponse(String responseBody) {
    if (responseBody == null || responseBody.isBlank()) {
      return "";
    }
    JsonNode root;
    try {
      root = objectMapper.readTree(responseBody);
    } catch (IOException e) {
      return ""; // not JSON (e.g. an error body) — nothing to screen
    }
    StringBuilder text = new StringBuilder();
    // Newer shape: output[].content[].text ; convenience shape: output_text
    JsonNode output = root.path("output");
    if (output.isArray()) {
      for (JsonNode item : output) {
        for (JsonNode part : item.path("content")) {
          if (part.path("text").isTextual()) {
            appendMasked(text, part.get("text").asText());
          }
        }
      }
    }
    if (text.length() == 0 && root.path("output_text").isTextual()) {
      appendMasked(text, root.get("output_text").asText());
    }
    return text.toString();
  }

  private void appendMasked(StringBuilder sink, String raw) {
    if (sink.length() > 0) {
      sink.append('\n');
    }
    sink.append(redaction.redact(raw).masked());
  }

  private CachedResponse forward(byte[] requestBody) {
    try {
      return openRouter
          .post()
          .uri("/responses")
          .contentType(MediaType.APPLICATION_JSON)
          .body(requestBody)
          .exchange(
              (request, response) -> {
                byte[] responseBody = response.getBody().readAllBytes();
                return new CachedResponse(
                    response.getStatusCode().value(),
                    MediaType.APPLICATION_JSON_VALUE,
                    new String(responseBody, StandardCharsets.UTF_8));
              });
    } catch (ResourceAccessException e) {
      throw new UpstreamUnavailableException(
          "The upstream model provider is unavailable or timed out.", e);
    }
  }

  private void validate(JsonNode body) {
    if (body == null || !body.isObject()) {
      throw new InvalidRequestException("Request body must be a JSON object.");
    }
    JsonNode model = body.get("model");
    if (model == null || !model.isTextual() || model.asText().isBlank()) {
      throw new InvalidRequestException("'model' is required and must be a non-empty string.");
    }
    JsonNode input = body.get("input");
    if (input == null || input.isNull() || (!input.isTextual() && !input.isArray())) {
      throw new InvalidRequestException("'input' is required and must be a string or an array.");
    }
    if (input.isTextual() && input.asText().isBlank()) {
      throw new InvalidRequestException("'input' must not be blank.");
    }
  }
}

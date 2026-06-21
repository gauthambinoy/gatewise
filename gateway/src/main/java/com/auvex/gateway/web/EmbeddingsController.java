package com.auvex.gateway.web;

import com.auvex.gateway.audit.AuditEntry;
import com.auvex.gateway.audit.AuditSink;
import com.auvex.gateway.audit.Verdict;
import com.auvex.gateway.auth.TenantContext;
import com.auvex.gateway.proxy.CachedResponse;
import com.auvex.gateway.proxy.UpstreamUnavailableException;
import com.auvex.gateway.redaction.Match;
import com.auvex.gateway.redaction.RedactionEngine;
import com.auvex.gateway.redaction.RedactionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
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
 * OpenAI-compatible embeddings endpoint, governed like the chat path: the input text is redacted
 * before it leaves and the call is recorded in the audit log, then it's forwarded to the provider.
 */
@RestController
@RequestMapping("/v1")
public class EmbeddingsController {

  private final RestClient openRouter;
  private final RedactionEngine redaction;
  private final AuditSink auditSink;
  private final ObjectMapper objectMapper;

  public EmbeddingsController(
      RestClient openRouterRestClient,
      RedactionEngine redaction,
      AuditSink auditSink,
      ObjectMapper objectMapper) {
    this.openRouter = openRouterRestClient;
    this.redaction = redaction;
    this.auditSink = auditSink;
    this.objectMapper = objectMapper;
  }

  /** Redacts the input, records the call, and forwards it to the provider's embeddings API. */
  @PostMapping("/embeddings")
  public ResponseEntity<String> embeddings(@RequestBody JsonNode body) throws IOException {
    validate(body);
    String model = body.get("model").asText();

    List<Match> found = new ArrayList<>();
    String redactedInput = redactInput((ObjectNode) body, found);

    UUID tenantId = TenantContext.require().tenantId();
    String actor =
        body.hasNonNull("user") && body.get("user").isTextual()
            ? body.get("user").asText()
            : "unknown";
    Verdict verdict = found.isEmpty() ? Verdict.ALLOWED : Verdict.REDACTED;
    auditSink.record(
        new AuditEntry(
            tenantId,
            UUID.randomUUID(),
            actor,
            model,
            verdict,
            redactedInput,
            null,
            Instant.now()));

    byte[] forwarded = objectMapper.writeValueAsBytes(body);
    CachedResponse response = forward(forwarded);
    return ResponseEntity.status(response.status())
        .contentType(MediaType.APPLICATION_JSON)
        .body(response.body());
  }

  // Mask sensitive data in the input (a string or an array of strings); return the masked text.
  private String redactInput(ObjectNode body, List<Match> found) {
    JsonNode input = body.get("input");
    StringBuilder text = new StringBuilder();
    if (input.isTextual()) {
      RedactionResult result = redaction.redact(input.asText());
      if (result.changed()) {
        body.put("input", result.masked());
        found.addAll(result.matches());
      }
      text.append(result.masked());
    } else if (input.isArray()) {
      ArrayNode array = (ArrayNode) input;
      for (int i = 0; i < array.size(); i++) {
        JsonNode element = array.get(i);
        if (!element.isTextual()) {
          continue;
        }
        RedactionResult result = redaction.redact(element.asText());
        if (text.length() > 0) {
          text.append('\n');
        }
        text.append(result.masked());
        if (result.changed()) {
          array.set(i, TextNode.valueOf(result.masked()));
          found.addAll(result.matches());
        }
      }
    }
    return text.toString();
  }

  private CachedResponse forward(byte[] requestBody) {
    try {
      return openRouter
          .post()
          .uri("/embeddings")
          .contentType(MediaType.APPLICATION_JSON)
          .body(requestBody)
          .exchange(
              (request, response) -> {
                byte[] body = response.getBody().readAllBytes();
                return new CachedResponse(
                    response.getStatusCode().value(),
                    MediaType.APPLICATION_JSON_VALUE,
                    new String(body, StandardCharsets.UTF_8));
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
    if (body.get("input") == null || body.get("input").isNull()) {
      throw new InvalidRequestException("'input' is required.");
    }
  }
}

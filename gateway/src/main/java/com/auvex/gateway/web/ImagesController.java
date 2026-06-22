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
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
 * OpenAI-compatible image-generation endpoint, governed like the chat path: the prompt is redacted
 * before it leaves and the call is recorded, then forwarded to the provider's images API.
 */
@RestController
@RequestMapping("/v1")
public class ImagesController {

  private final RestClient openRouter;
  private final RedactionEngine redaction;
  private final AuditSink auditSink;
  private final ObjectMapper objectMapper;

  public ImagesController(
      RestClient openRouterRestClient,
      RedactionEngine redaction,
      AuditSink auditSink,
      ObjectMapper objectMapper) {
    this.openRouter = openRouterRestClient;
    this.redaction = redaction;
    this.auditSink = auditSink;
    this.objectMapper = objectMapper;
  }

  /** Redacts the prompt, records the call, and forwards it to the provider's images API. */
  @PostMapping("/images/generations")
  public ResponseEntity<String> generate(@RequestBody JsonNode body) throws IOException {
    validate(body);
    RedactionResult result = redaction.redact(body.get("prompt").asText());
    if (result.changed()) {
      ((ObjectNode) body).put("prompt", result.masked());
    }

    UUID tenantId = TenantContext.require().tenantId();
    String actor =
        body.hasNonNull("user") && body.get("user").isTextual()
            ? body.get("user").asText()
            : "unknown";
    Verdict verdict = result.changed() ? Verdict.REDACTED : Verdict.ALLOWED;
    auditSink.record(
        new AuditEntry(
            tenantId,
            UUID.randomUUID(),
            actor,
            body.get("model").asText(),
            verdict,
            result.masked(),
            null,
            Instant.now()));

    byte[] forwarded = objectMapper.writeValueAsBytes(body);
    CachedResponse response = forward(forwarded);
    return ResponseEntity.status(response.status())
        .contentType(MediaType.APPLICATION_JSON)
        .body(response.body());
  }

  private CachedResponse forward(byte[] requestBody) {
    try {
      return openRouter
          .post()
          .uri("/images/generations")
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
    JsonNode prompt = body.get("prompt");
    if (prompt == null || !prompt.isTextual() || prompt.asText().isBlank()) {
      throw new InvalidRequestException("'prompt' is required and must be a non-empty string.");
    }
  }
}

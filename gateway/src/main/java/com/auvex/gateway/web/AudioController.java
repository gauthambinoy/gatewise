package com.auvex.gateway.web;

import com.auvex.gateway.audit.AuditEntry;
import com.auvex.gateway.audit.AuditSink;
import com.auvex.gateway.audit.Verdict;
import com.auvex.gateway.auth.TenantContext;
import com.auvex.gateway.redaction.RedactionEngine;
import com.auvex.gateway.redaction.RedactionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

/**
 * Governed audio endpoints.
 *
 * <p>{@code /v1/audio/speech} (text-to-speech) redacts the input text before it leaves, records the
 * call, and streams back the provider's audio bytes. {@code /v1/audio/transcriptions}
 * (speech-to-text) forwards the uploaded audio and screens the returned transcript for PII into the
 * audit log — the model can transcribe sensitive speech, so the output needs governing just like a
 * chat response.
 */
@RestController
@RequestMapping("/v1")
public class AudioController {

  private final RestClient openRouter;
  private final RedactionEngine redaction;
  private final AuditSink auditSink;
  private final ObjectMapper objectMapper;

  public AudioController(
      RestClient openRouterRestClient,
      RedactionEngine redaction,
      AuditSink auditSink,
      ObjectMapper objectMapper) {
    this.openRouter = openRouterRestClient;
    this.redaction = redaction;
    this.auditSink = auditSink;
    this.objectMapper = objectMapper;
  }

  /** Redacts the input text, records the call, and returns the synthesized audio. */
  @PostMapping("/audio/speech")
  public ResponseEntity<byte[]> speech(@RequestBody JsonNode body) throws IOException {
    if (body == null || !body.isObject()) {
      throw new InvalidRequestException("Request body must be a JSON object.");
    }
    JsonNode model = body.get("model");
    if (model == null || !model.isTextual() || model.asText().isBlank()) {
      throw new InvalidRequestException("'model' is required and must be a non-empty string.");
    }
    JsonNode input = body.get("input");
    if (input == null || !input.isTextual() || input.asText().isBlank()) {
      throw new InvalidRequestException("'input' is required and must be a non-empty string.");
    }

    RedactionResult result = redaction.redact(input.asText());
    if (result.changed()) {
      ((ObjectNode) body).put("input", result.masked());
    }

    UUID tenantId = TenantContext.require().tenantId();
    auditSink.record(
        new AuditEntry(
            tenantId,
            UUID.randomUUID(),
            "unknown",
            model.asText(),
            result.changed() ? Verdict.REDACTED : Verdict.ALLOWED,
            result.masked(),
            null,
            Instant.now()));

    byte[] forwarded = objectMapper.writeValueAsBytes(body);
    try {
      return openRouter
          .post()
          .uri("/audio/speech")
          .contentType(MediaType.APPLICATION_JSON)
          .body(forwarded)
          .exchange(
              (request, response) -> {
                byte[] audio = response.getBody().readAllBytes();
                MediaType contentType = response.getHeaders().getContentType();
                return ResponseEntity.status(response.getStatusCode().value())
                    .contentType(contentType == null ? MediaType.valueOf("audio/mpeg") : contentType)
                    .body(audio);
              });
    } catch (ResourceAccessException e) {
      throw new com.auvex.gateway.proxy.UpstreamUnavailableException(
          "The upstream model provider is unavailable or timed out.", e);
    }
  }

  /** Forwards the uploaded audio and screens the returned transcript into the audit log. */
  @PostMapping(value = "/audio/transcriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<String> transcribe(
      @RequestParam("file") MultipartFile file, @RequestParam("model") String model)
      throws IOException {
    if (file == null || file.isEmpty()) {
      throw new InvalidRequestException("'file' is required.");
    }
    if (model == null || model.isBlank()) {
      throw new InvalidRequestException("'model' is required.");
    }

    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    String filename = file.getOriginalFilename() == null ? "audio" : file.getOriginalFilename();
    form.add(
        "file",
        new ByteArrayResource(file.getBytes()) {
          @Override
          public String getFilename() {
            return filename;
          }
        });
    form.add("model", model);

    String responseBody;
    int status;
    try {
      ResponseEntity<String> upstream =
          openRouter
              .post()
              .uri("/audio/transcriptions")
              .contentType(MediaType.MULTIPART_FORM_DATA)
              .body(form)
              .retrieve()
              .toEntity(String.class);
      responseBody = upstream.getBody();
      status = upstream.getStatusCode().value();
    } catch (ResourceAccessException e) {
      throw new com.auvex.gateway.proxy.UpstreamUnavailableException(
          "The upstream model provider is unavailable or timed out.", e);
    }

    // Screen the transcript text for PII (audit-only) — the spoken content may be sensitive.
    String transcript = transcriptText(responseBody);
    RedactionResult screened = redaction.redact(transcript);
    auditSink.record(
        new AuditEntry(
            TenantContext.require().tenantId(),
            UUID.randomUUID(),
            "unknown",
            model,
            screened.changed() ? Verdict.REDACTED : Verdict.ALLOWED,
            null,
            screened.changed() ? screened.masked() : null,
            Instant.now()));

    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_JSON)
        .body(responseBody);
  }

  private String transcriptText(String responseBody) {
    if (responseBody == null || responseBody.isBlank()) {
      return "";
    }
    try {
      JsonNode text = objectMapper.readTree(responseBody).path("text");
      return text.isTextual() ? text.asText() : "";
    } catch (IOException e) {
      return ""; // not JSON (e.g. an error body) — nothing to screen
    }
  }
}

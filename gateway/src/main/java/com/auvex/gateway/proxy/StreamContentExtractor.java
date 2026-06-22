package com.auvex.gateway.proxy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Reassembles the assistant's text from a captured OpenAI-style SSE stream — concatenating each
 * chunk's {@code choices[0].delta.content} — so a streamed response can be screened and audited
 * like a buffered one. Malformed or non-data lines (and the {@code [DONE]} sentinel) are skipped.
 */
@Component
public class StreamContentExtractor {

  private final ObjectMapper objectMapper;

  public StreamContentExtractor(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** The assembled assistant content from a captured SSE body (empty if none). */
  public String extract(String sse) {
    StringBuilder content = new StringBuilder();
    for (String rawLine : sse.split("\n")) {
      String line = rawLine.strip();
      if (!line.startsWith("data:")) {
        continue;
      }
      String data = line.substring("data:".length()).strip();
      if (data.isEmpty() || "[DONE]".equals(data)) {
        continue;
      }
      try {
        JsonNode delta =
            objectMapper.readTree(data).path("choices").path(0).path("delta").path("content");
        if (delta.isTextual()) {
          content.append(delta.asText());
        }
      } catch (JsonProcessingException e) {
        // Skip a malformed chunk rather than fail the whole capture.
      }
    }
    return content.toString();
  }
}

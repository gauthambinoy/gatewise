package com.auvex.gateway.pricing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** Pulls token usage out of an OpenAI-style chat-completions response body. */
@Component
public class UsageExtractor {

  private final ObjectMapper json;

  public UsageExtractor(ObjectMapper json) {
    this.json = json;
  }

  /** Returns the usage block, or null if the response has none (or can't be parsed). */
  public TokenUsage extract(String responseBody) {
    if (responseBody == null || responseBody.isBlank()) {
      return null;
    }
    try {
      JsonNode usage = json.readTree(responseBody).path("usage");
      if (!usage.hasNonNull("prompt_tokens")) {
        return null;
      }
      return new TokenUsage(
          usage.path("prompt_tokens").asInt(), usage.path("completion_tokens").asInt());
    } catch (JsonProcessingException e) {
      return null; // best-effort: an unparseable body just means "no usage"
    }
  }
}

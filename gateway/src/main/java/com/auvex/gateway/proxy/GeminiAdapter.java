package com.auvex.gateway.proxy;

import com.auvex.gateway.config.GeminiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Google Gemini's native {@code generateContent} API. Claims {@code google/*} models when enabled,
 * translating the request and response to and from the OpenAI shape via {@link GeminiTranslator}.
 */
@Component
@Order(1)
public class GeminiAdapter implements ProviderAdapter {

  private static final String PREFIX = "google/";

  private final RestClient gemini;
  private final GeminiTranslator translator;
  private final GeminiProperties properties;
  private final ObjectMapper json;

  public GeminiAdapter(
      RestClient geminiRestClient,
      GeminiTranslator translator,
      GeminiProperties properties,
      ObjectMapper json) {
    this.gemini = geminiRestClient;
    this.translator = translator;
    this.properties = properties;
    this.json = json;
  }

  @Override
  public boolean supports(String model) {
    return properties.enabled() && model.startsWith(PREFIX);
  }

  @Override
  public CachedResponse fetch(byte[] requestBody) {
    String model = geminiModel(requestBody);
    byte[] geminiRequest = translator.toGeminiRequest(requestBody);
    try {
      return gemini
          .post()
          .uri("/v1beta/models/{model}:generateContent", model)
          .contentType(MediaType.APPLICATION_JSON)
          .body(geminiRequest)
          .exchange(
              (request, response) -> {
                int status = response.getStatusCode().value();
                String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                String openAiBody =
                    response.getStatusCode().is2xxSuccessful()
                        ? translator.toOpenAiResponse(body)
                        : body; // surface provider error bodies as-is
                return new CachedResponse(status, MediaType.APPLICATION_JSON_VALUE, openAiBody);
              });
    } catch (ResourceAccessException e) {
      throw new UpstreamUnavailableException("The Gemini provider is unavailable or timed out.", e);
    }
  }

  /**
   * Reads the resolved model from the request and strips the {@code google/} prefix for the URL.
   */
  private String geminiModel(byte[] requestBody) {
    try {
      JsonNode in = json.readTree(requestBody);
      String model = in.path("model").asText("");
      return model.startsWith(PREFIX) ? model.substring(PREFIX.length()) : model;
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Failed to read request for Gemini translation", e);
    }
  }
}

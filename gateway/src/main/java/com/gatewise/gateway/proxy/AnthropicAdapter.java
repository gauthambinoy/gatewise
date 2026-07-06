package com.gatewise.gateway.proxy;

import com.gatewise.gateway.config.AnthropicProperties;
import java.nio.charset.StandardCharsets;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Anthropic's native {@code /v1/messages} API. Claims {@code anthropic/*} models when enabled,
 * translating the request and response to and from the OpenAI shape via {@link
 * AnthropicTranslator}.
 */
@Component
@Order(0)
public class AnthropicAdapter implements ProviderAdapter {

  private static final String PREFIX = "anthropic/";

  private final RestClient anthropic;
  private final AnthropicTranslator translator;
  private final AnthropicProperties properties;

  public AnthropicAdapter(
      RestClient anthropicRestClient,
      AnthropicTranslator translator,
      AnthropicProperties properties) {
    this.anthropic = anthropicRestClient;
    this.translator = translator;
    this.properties = properties;
  }

  @Override
  public boolean supports(String model) {
    return properties.enabled() && model.startsWith(PREFIX);
  }

  @Override
  public CachedResponse fetch(byte[] requestBody) {
    byte[] anthropicRequest = translator.toAnthropicRequest(requestBody);
    try {
      return anthropic
          .post()
          .uri("/v1/messages")
          .contentType(MediaType.APPLICATION_JSON)
          .body(anthropicRequest)
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
      throw new UpstreamUnavailableException(
          "The Anthropic provider is unavailable or timed out.", e);
    }
  }
}

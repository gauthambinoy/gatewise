package com.auvex.gateway.proxy;

import com.auvex.gateway.config.AnthropicProperties;
import com.auvex.gateway.config.FailoverProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Forwards a chat-completions request to the right upstream provider and relays the response.
 *
 * <p>The streaming path ({@link #relay}) copies the response straight through. The buffered path
 * ({@link #fetch}) reads the whole response — what caching, failover and response redaction need.
 * By default the request is sent to the OpenAI-compatible primary (with failover); when native
 * Anthropic is enabled, {@code anthropic/*} models are translated and sent directly to Anthropic's
 * API, and the response is translated back to the OpenAI shape so everything downstream is
 * provider-agnostic.
 */
@Component
public class UpstreamProxy {

  private static final String ANTHROPIC_PREFIX = "anthropic/";

  private final RestClient openRouter;
  private final RestClient failoverClient;
  private final RestClient anthropic;
  private final FailoverProperties failover;
  private final AnthropicProperties anthropicProperties;
  private final AnthropicTranslator anthropicTranslator;
  private final ObjectMapper objectMapper;

  public UpstreamProxy(
      RestClient openRouterRestClient,
      RestClient failoverRestClient,
      RestClient anthropicRestClient,
      FailoverProperties failover,
      AnthropicProperties anthropicProperties,
      AnthropicTranslator anthropicTranslator,
      ObjectMapper objectMapper) {
    this.openRouter = openRouterRestClient;
    this.failoverClient = failoverRestClient;
    this.anthropic = anthropicRestClient;
    this.failover = failover;
    this.anthropicProperties = anthropicProperties;
    this.anthropicTranslator = anthropicTranslator;
    this.objectMapper = objectMapper;
  }

  /**
   * Sends {@code requestBody} to the provider and copies the response into {@code clientResponse}.
   * Streaming always uses the OpenAI-compatible primary (native Anthropic streaming is a
   * follow-up).
   */
  public void relay(byte[] requestBody, HttpServletResponse clientResponse) {
    try {
      openRouter
          .post()
          .uri("/chat/completions")
          .contentType(MediaType.APPLICATION_JSON)
          .body(requestBody)
          .exchange(
              (request, response) -> {
                // Mirror the provider's status and content type, then stream the body.
                clientResponse.setStatus(response.getStatusCode().value());
                MediaType contentType = response.getHeaders().getContentType();
                if (contentType != null) {
                  clientResponse.setContentType(contentType.toString());
                }
                try (InputStream upstream = response.getBody()) {
                  upstream.transferTo(clientResponse.getOutputStream());
                }
                clientResponse.flushBuffer();
                return null;
              });
    } catch (ResourceAccessException e) {
      // Connection refused / timed out before we got a response — a clean 504 case.
      throw new UpstreamUnavailableException(
          "The upstream model provider is unavailable or timed out.", e);
    }
  }

  /**
   * Forwards the request and returns the fully-buffered response (non-streaming path).
   *
   * <p>An {@code anthropic/*} model (when native Anthropic is enabled) goes straight to Anthropic.
   * Otherwise the OpenAI-compatible primary is used, with failover to the fallback on a connection
   * failure or 5xx. Only when both fail does this raise a 504.
   */
  public CachedResponse fetch(byte[] requestBody) {
    if (anthropicProperties.enabled() && isAnthropicModel(requestBody)) {
      return fetchFromAnthropic(requestBody);
    }
    CachedResponse primary;
    try {
      primary = fetchFrom(openRouter, requestBody);
    } catch (ResourceAccessException e) {
      return failoverOrFail(requestBody, e);
    }
    if (failover.enabled() && primary.status() >= 500) {
      try {
        return fetchFrom(failoverClient, requestBody);
      } catch (ResourceAccessException e) {
        return primary; // fallback is down too — surface the primary's 5xx
      }
    }
    return primary;
  }

  // True when the resolved model targets Anthropic's native API.
  private boolean isAnthropicModel(byte[] requestBody) {
    try {
      return objectMapper
          .readTree(requestBody)
          .path("model")
          .asText("")
          .startsWith(ANTHROPIC_PREFIX);
    } catch (IOException e) {
      return false;
    }
  }

  // Translate OpenAI → Anthropic, call /v1/messages, translate the response back to OpenAI.
  private CachedResponse fetchFromAnthropic(byte[] requestBody) {
    byte[] anthropicRequest = anthropicTranslator.toAnthropicRequest(requestBody);
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
                        ? anthropicTranslator.toOpenAiResponse(body)
                        : body; // surface provider error bodies as-is
                return new CachedResponse(status, MediaType.APPLICATION_JSON_VALUE, openAiBody);
              });
    } catch (ResourceAccessException e) {
      throw new UpstreamUnavailableException(
          "The Anthropic provider is unavailable or timed out.", e);
    }
  }

  private CachedResponse failoverOrFail(byte[] requestBody, ResourceAccessException cause) {
    if (failover.enabled()) {
      try {
        return fetchFrom(failoverClient, requestBody);
      } catch (ResourceAccessException e) {
        throw new UpstreamUnavailableException(
            "Both the primary and fallback providers are unavailable.", e);
      }
    }
    throw new UpstreamUnavailableException(
        "The upstream model provider is unavailable or timed out.", cause);
  }

  private static CachedResponse fetchFrom(RestClient client, byte[] requestBody) {
    return client
        .post()
        .uri("/chat/completions")
        .contentType(MediaType.APPLICATION_JSON)
        .body(requestBody)
        .exchange(
            (request, response) -> {
              MediaType contentType = response.getHeaders().getContentType();
              byte[] body = response.getBody().readAllBytes();
              return new CachedResponse(
                  response.getStatusCode().value(),
                  contentType != null ? contentType.toString() : MediaType.APPLICATION_JSON_VALUE,
                  new String(body, StandardCharsets.UTF_8));
            });
  }
}

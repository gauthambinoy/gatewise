package com.auvex.gateway.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Forwards a chat-completions request to the right upstream provider and relays the response.
 *
 * <p>The buffered path ({@link #fetch}) picks a {@link ProviderAdapter} by the resolved model and
 * delegates — so the provider list is open for extension (Anthropic, Gemini, …) with no change
 * here. The streaming path ({@link #relay}) copies the response straight through the
 * OpenAI-compatible primary (provider-native streaming is a follow-up).
 */
@Component
public class UpstreamProxy {

  private final List<ProviderAdapter> adapters;
  private final RestClient openRouter;
  private final ObjectMapper objectMapper;

  public UpstreamProxy(
      List<ProviderAdapter> adapters, RestClient openRouterRestClient, ObjectMapper objectMapper) {
    this.adapters = adapters;
    this.openRouter = openRouterRestClient;
    this.objectMapper = objectMapper;
  }

  /**
   * Sends {@code requestBody} to the provider and copies the response into {@code clientResponse}.
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

  /** Forwards the request and returns the fully-buffered response, via the matching adapter. */
  public CachedResponse fetch(byte[] requestBody) {
    String model = modelOf(requestBody);
    for (ProviderAdapter adapter : adapters) {
      if (adapter.supports(model)) {
        return adapter.fetch(requestBody);
      }
    }
    // The OpenAI-compatible adapter is a catch-all, so this is unreachable in practice.
    throw new UpstreamUnavailableException("No provider adapter for model '" + model + "'.", null);
  }

  private String modelOf(byte[] requestBody) {
    try {
      return objectMapper.readTree(requestBody).path("model").asText("");
    } catch (IOException e) {
      return "";
    }
  }
}

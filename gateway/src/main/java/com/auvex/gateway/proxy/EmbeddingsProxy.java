package com.auvex.gateway.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Forwards an embeddings request to the right upstream provider and returns the buffered response.
 *
 * <p>Mirrors {@link UpstreamProxy} for the embeddings path: it picks an {@link EmbeddingsAdapter}
 * by the resolved model and delegates — so the provider list is open for extension (Gemini, …) with
 * no change here. The OpenAI-compatible adapter is the lowest-precedence catch-all, so every
 * request matches at least it.
 */
@Component
public class EmbeddingsProxy {

  private final List<EmbeddingsAdapter> adapters;
  private final ObjectMapper objectMapper;

  /**
   * Creates the proxy over the available adapters (Spring orders them by {@code @Order}).
   *
   * @param adapters the embeddings adapters, lowest {@code @Order} value first
   * @param objectMapper used to read the resolved model from the request body
   */
  public EmbeddingsProxy(List<EmbeddingsAdapter> adapters, ObjectMapper objectMapper) {
    this.adapters = adapters;
    this.objectMapper = objectMapper;
  }

  /** Forwards the request and returns the fully-buffered response, via the matching adapter. */
  public CachedResponse embed(byte[] requestBody) {
    String model = modelOf(requestBody);
    for (EmbeddingsAdapter adapter : adapters) {
      if (adapter.supports(model)) {
        return adapter.embed(requestBody);
      }
    }
    // The OpenAI-compatible adapter is a catch-all, so this is unreachable in practice.
    throw new UpstreamUnavailableException(
        "No embeddings adapter for model '" + model + "'.", null);
  }

  private String modelOf(byte[] requestBody) {
    try {
      return objectMapper.readTree(requestBody).path("model").asText("");
    } catch (IOException e) {
      return "";
    }
  }
}

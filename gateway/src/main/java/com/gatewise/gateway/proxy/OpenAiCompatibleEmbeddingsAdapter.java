package com.gatewise.gateway.proxy;

import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * The default embeddings provider: any OpenAI-compatible endpoint (e.g. OpenRouter). It is the
 * lowest-precedence catch-all — it {@link #supports} every model, so an embeddings request that no
 * specific adapter claims lands here and is forwarded unchanged to {@code /embeddings}.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class OpenAiCompatibleEmbeddingsAdapter implements EmbeddingsAdapter {

  private final RestClient openRouter;

  /**
   * Creates the catch-all adapter.
   *
   * @param openRouterRestClient the OpenAI-compatible primary client
   */
  public OpenAiCompatibleEmbeddingsAdapter(RestClient openRouterRestClient) {
    this.openRouter = openRouterRestClient;
  }

  @Override
  public boolean supports(String model) {
    return true; // catch-all fallback
  }

  @Override
  public CachedResponse embed(byte[] requestBody) {
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
}

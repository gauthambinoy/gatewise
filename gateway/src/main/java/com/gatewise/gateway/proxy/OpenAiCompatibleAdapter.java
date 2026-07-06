package com.gatewise.gateway.proxy;

import com.gatewise.gateway.config.FailoverProperties;
import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * The default provider: any OpenAI-compatible endpoint (e.g. OpenRouter), with failover. It is the
 * lowest-precedence catch-all — it {@link #supports} every model, so a request that no specific
 * adapter claims lands here.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class OpenAiCompatibleAdapter implements ProviderAdapter {

  private final RestClient openRouter;
  private final RestClient failoverClient;
  private final FailoverProperties failover;

  public OpenAiCompatibleAdapter(
      RestClient openRouterRestClient, RestClient failoverRestClient, FailoverProperties failover) {
    this.openRouter = openRouterRestClient;
    this.failoverClient = failoverRestClient;
    this.failover = failover;
  }

  @Override
  public boolean supports(String model) {
    return true; // catch-all fallback
  }

  @Override
  public CachedResponse fetch(byte[] requestBody) {
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

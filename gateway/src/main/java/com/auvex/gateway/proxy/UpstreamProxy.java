package com.auvex.gateway.proxy;

import com.auvex.gateway.config.FailoverProperties;
import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Forwards a chat-completions request to the upstream provider and relays the response.
 *
 * <p>The streaming path ({@link #relay}) copies the response straight through to the caller. The
 * buffered path ({@link #fetch}) reads the whole response — which is what caching and provider
 * failover need. The upstream status code is passed through verbatim (a provider 429 stays a 429).
 */
@Component
public class UpstreamProxy {

  private final RestClient openRouter;
  private final RestClient failoverClient;
  private final FailoverProperties failover;

  public UpstreamProxy(
      RestClient openRouterRestClient, RestClient failoverRestClient, FailoverProperties failover) {
    this.openRouter = openRouterRestClient;
    this.failoverClient = failoverRestClient;
    this.failover = failover;
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

  /**
   * Forwards the request and returns the fully-buffered response (non-streaming path).
   *
   * <p>If the primary can't be reached, or returns a 5xx, and failover is enabled, the fallback
   * provider is tried. Only when both fail does this raise a 504.
   */
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

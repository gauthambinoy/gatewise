package com.auvex.gateway.proxy;

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
 * <p>The response is streamed straight through to the caller rather than buffered, so a normal JSON
 * reply and a server-sent-events stream are handled the same way — and on a virtual thread the
 * blocking copy costs almost nothing. The upstream status code is passed through verbatim (a
 * provider 429 stays a 429), so callers see the real provider behaviour.
 */
@Component
public class UpstreamProxy {

  private final RestClient openRouter;

  public UpstreamProxy(RestClient openRouterRestClient) {
    this.openRouter = openRouterRestClient;
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
   * Forwards the request and returns the fully-buffered response, for the cacheable (non-streaming)
   * path. The status is passed through as-is so the caller can decide whether to cache it.
   */
  public CachedResponse fetch(byte[] requestBody) {
    try {
      return openRouter
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
    } catch (ResourceAccessException e) {
      throw new UpstreamUnavailableException(
          "The upstream model provider is unavailable or timed out.", e);
    }
  }
}

package com.auvex.gateway.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
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

  // Cap how much of a streamed response we copy for auditing, so a huge stream can't exhaust
  // memory.
  private static final int CAPTURE_LIMIT = 512 * 1024;

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
   * Sends {@code requestBody} to the provider, streams the response straight to {@code
   * clientResponse}, and returns a capped copy of the bytes streamed — so the streamed response can
   * still be captured and audited after the fact, without holding up the stream.
   */
  public byte[] relay(byte[] requestBody, HttpServletResponse clientResponse) {
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
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
                  ServletOutputStream out = clientResponse.getOutputStream();
                  byte[] buffer = new byte[8192];
                  int read;
                  while ((read = upstream.read(buffer)) != -1) {
                    out.write(buffer, 0, read); // tee to the caller, uninterrupted
                    int room = CAPTURE_LIMIT - capture.size();
                    if (room > 0) {
                      capture.write(buffer, 0, Math.min(read, room)); // and to the audit copy
                    }
                  }
                }
                clientResponse.flushBuffer();
                return null;
              });
    } catch (ResourceAccessException e) {
      // Connection refused / timed out before we got a response — a clean 504 case.
      throw new UpstreamUnavailableException(
          "The upstream model provider is unavailable or timed out.", e);
    }
    return capture.toByteArray();
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

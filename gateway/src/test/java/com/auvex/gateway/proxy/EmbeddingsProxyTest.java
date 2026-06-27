package com.auvex.gateway.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies {@link EmbeddingsProxy} routes to the first adapter that supports the model. */
class EmbeddingsProxyTest {

  private final ObjectMapper json = new ObjectMapper();

  // A test adapter that claims models with a given prefix (or everything, when prefix is null).
  private static final class FakeAdapter implements EmbeddingsAdapter {
    private final String prefix;
    private final String label;

    FakeAdapter(String prefix, String label) {
      this.prefix = prefix;
      this.label = label;
    }

    @Override
    public boolean supports(String model) {
      return prefix == null || model.startsWith(prefix);
    }

    @Override
    public CachedResponse embed(byte[] openAiEmbeddingsRequest) {
      return new CachedResponse(200, "application/json", "{\"by\":\"" + label + "\"}");
    }
  }

  @Test
  void picksTheFirstSupportingAdapter() {
    FakeAdapter gemini = new FakeAdapter("google/", "gemini");
    FakeAdapter catchAll = new FakeAdapter(null, "catch-all");
    EmbeddingsProxy proxy = new EmbeddingsProxy(List.of(gemini, catchAll), json);

    CachedResponse google =
        proxy.embed(
            "{\"model\":\"google/text-embedding-004\",\"input\":\"x\"}"
                .getBytes(StandardCharsets.UTF_8));
    assertThat(google.body()).contains("gemini");
  }

  @Test
  void fallsThroughToTheCatchAll() {
    FakeAdapter gemini = new FakeAdapter("google/", "gemini");
    FakeAdapter catchAll = new FakeAdapter(null, "catch-all");
    EmbeddingsProxy proxy = new EmbeddingsProxy(List.of(gemini, catchAll), json);

    CachedResponse other =
        proxy.embed(
            "{\"model\":\"text-embedding-3-small\",\"input\":\"x\"}"
                .getBytes(StandardCharsets.UTF_8));
    assertThat(other.body()).contains("catch-all");
  }

  @Test
  void noMatchingAdapterFails() {
    // A degenerate setup with no catch-all: the proxy has nothing to delegate to.
    EmbeddingsProxy proxy =
        new EmbeddingsProxy(List.of(new FakeAdapter("google/", "gemini")), json);

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                proxy.embed(
                    "{\"model\":\"text-embedding-3-small\",\"input\":\"x\"}"
                        .getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(UpstreamUnavailableException.class);
  }
}

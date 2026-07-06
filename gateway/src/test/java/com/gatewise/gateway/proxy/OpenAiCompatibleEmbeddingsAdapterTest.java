package com.gatewise.gateway.proxy;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Proves the catch-all adapter forwards the OpenAI embeddings request to {@code /embeddings}
 * unchanged.
 */
class OpenAiCompatibleEmbeddingsAdapterTest {

  private MockWebServer upstream;
  private OpenAiCompatibleEmbeddingsAdapter adapter;

  @BeforeEach
  void start() throws IOException {
    upstream = new MockWebServer();
    upstream.start();
    RestClient client =
        RestClient.builder().baseUrl("http://localhost:" + upstream.getPort()).build();
    adapter = new OpenAiCompatibleEmbeddingsAdapter(client);
  }

  @AfterEach
  void stop() throws IOException {
    upstream.shutdown();
  }

  @Test
  void supportsEveryModel() {
    assertThat(adapter.supports("text-embedding-3-small")).isTrue();
    assertThat(adapter.supports("anything")).isTrue();
  }

  @Test
  void forwardsRequestUnchangedAndRelaysResponse() throws Exception {
    String upstreamBody = "{\"object\":\"list\",\"data\":[{\"index\":0,\"embedding\":[0.5]}]}";
    upstream.enqueue(
        new MockResponse().setHeader("Content-Type", "application/json").setBody(upstreamBody));

    String request = "{\"model\":\"text-embedding-3-small\",\"input\":\"hi\"}";
    CachedResponse response = adapter.embed(request.getBytes(UTF_8));

    // Forwarded verbatim to /embeddings.
    RecordedRequest sent = upstream.takeRequest();
    assertThat(sent.getPath()).isEqualTo("/embeddings");
    assertThat(sent.getBody().readUtf8()).isEqualTo(request);

    // Response relayed back unchanged.
    assertThat(response.status()).isEqualTo(200);
    assertThat(response.body()).isEqualTo(upstreamBody);
  }
}

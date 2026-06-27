package com.auvex.gateway.proxy;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.auvex.gateway.config.GeminiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Proves a {@code google/*} embeddings request is translated to Gemini's native embeddings call and
 * the Gemini response is mapped back to the OpenAI embeddings shape.
 */
class GeminiEmbeddingsAdapterTest {

  private final ObjectMapper json = new ObjectMapper();
  private MockWebServer gemini;
  private GeminiEmbeddingsAdapter adapter;

  @BeforeEach
  void start() throws IOException {
    gemini = new MockWebServer();
    gemini.start();
    RestClient client =
        RestClient.builder().baseUrl("http://localhost:" + gemini.getPort()).build();
    adapter =
        new GeminiEmbeddingsAdapter(client, new GeminiProperties(true, null, "k", 1024), json);
  }

  @AfterEach
  void stop() throws IOException {
    gemini.shutdown();
  }

  @Test
  void claimsGoogleModelsOnlyWhenEnabled() {
    assertThat(adapter.supports("google/text-embedding-004")).isTrue();
    assertThat(adapter.supports("text-embedding-3-small")).isFalse();

    GeminiEmbeddingsAdapter disabled =
        new GeminiEmbeddingsAdapter(
            RestClient.builder().build(), new GeminiProperties(false, null, "k", 1024), json);
    assertThat(disabled.supports("google/text-embedding-004")).isFalse();
  }

  @Test
  void translatesSingleInputToEmbedContentAndMapsBack() throws Exception {
    gemini.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("{\"embedding\":{\"values\":[0.1,0.2,0.3]}}"));

    String request = "{\"model\":\"google/text-embedding-004\",\"input\":\"hello world\"}";
    CachedResponse response = adapter.embed(request.getBytes(UTF_8));

    // The gateway called Gemini's native single-embedding endpoint with the prefix-stripped model.
    RecordedRequest sent = gemini.takeRequest();
    assertThat(sent.getPath()).isEqualTo("/v1beta/models/text-embedding-004:embedContent");
    String sentBody = sent.getBody().readUtf8();
    assertThat(sentBody).contains("\"parts\"").contains("hello world");
    assertThat(sentBody).doesNotContain("google/"); // the prefix went in the URL, not the body

    // The caller gets an OpenAI-shaped embeddings response, mapped from Gemini's.
    JsonNode out = json.readTree(response.body());
    assertThat(response.status()).isEqualTo(200);
    assertThat(out.get("object").asText()).isEqualTo("list");
    assertThat(out.get("model").asText()).isEqualTo("text-embedding-004");
    JsonNode first = out.get("data").get(0);
    assertThat(first.get("object").asText()).isEqualTo("embedding");
    assertThat(first.get("index").asInt()).isEqualTo(0);
    assertThat(first.get("embedding").get(0).asDouble()).isEqualTo(0.1);
    assertThat(first.get("embedding").get(1).asDouble()).isEqualTo(0.2);
    assertThat(first.get("embedding").get(2).asDouble()).isEqualTo(0.3);
  }

  @Test
  void translatesArrayInputToBatchEmbedContentsAndMapsBack() throws Exception {
    gemini.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("{\"embeddings\":[{\"values\":[1.5,2.5]},{\"values\":[3.5,4.5]}]}"));

    String request = "{\"model\":\"google/text-embedding-004\",\"input\":[\"a\",\"b\"]}";
    CachedResponse response = adapter.embed(request.getBytes(UTF_8));

    RecordedRequest sent = gemini.takeRequest();
    assertThat(sent.getPath()).isEqualTo("/v1beta/models/text-embedding-004:batchEmbedContents");
    assertThat(sent.getBody().readUtf8()).contains("\"requests\"");

    JsonNode out = json.readTree(response.body());
    assertThat(out.get("object").asText()).isEqualTo("list");
    assertThat(out.get("data").size()).isEqualTo(2);
    assertThat(out.get("data").get(0).get("index").asInt()).isEqualTo(0);
    assertThat(out.get("data").get(0).get("embedding").get(1).asDouble()).isEqualTo(2.5);
    assertThat(out.get("data").get(1).get("index").asInt()).isEqualTo(1);
    assertThat(out.get("data").get(1).get("embedding").get(0).asDouble()).isEqualTo(3.5);
  }

  @Test
  void surfacesGeminiErrorBodyUnchanged() throws Exception {
    gemini.enqueue(
        new MockResponse()
            .setResponseCode(400)
            .setHeader("Content-Type", "application/json")
            .setBody("{\"error\":{\"message\":\"bad model\"}}"));

    CachedResponse response =
        adapter.embed("{\"model\":\"google/text-embedding-004\",\"input\":\"x\"}".getBytes(UTF_8));

    assertThat(response.status()).isEqualTo(400);
    assertThat(response.body()).contains("bad model");
  }
}

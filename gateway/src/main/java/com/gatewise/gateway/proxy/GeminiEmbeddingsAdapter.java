package com.gatewise.gateway.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gatewise.gateway.config.GeminiProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Google Gemini's native embeddings API. Claims {@code google/*} models when enabled, translating
 * the OpenAI embeddings request and the Gemini response to and from the OpenAI shape.
 *
 * <p>A single text input is sent to {@code :embedContent}; an array of inputs is sent to {@code
 * :batchEmbedContents}. The Gemini {@code {embedding:{values:[…]}}} / {@code
 * {embeddings:[{values:[…]}]}} response is mapped back to the OpenAI {@code {object:"list",
 * data:[{embedding:[…], index}], model, usage}} shape, so everything downstream (the audit record)
 * only ever sees OpenAI JSON.
 */
@Component
@Order(1)
public class GeminiEmbeddingsAdapter implements EmbeddingsAdapter {

  private static final String PREFIX = "google/";

  private final RestClient gemini;
  private final GeminiProperties properties;
  private final ObjectMapper json;

  /**
   * Creates the Gemini embeddings adapter.
   *
   * @param geminiRestClient the Gemini native API client ({@code x-goog-api-key})
   * @param properties Gemini provider settings (the {@code enabled} gate)
   * @param json JSON codec for the request/response translation
   */
  public GeminiEmbeddingsAdapter(
      RestClient geminiRestClient, GeminiProperties properties, ObjectMapper json) {
    this.gemini = geminiRestClient;
    this.properties = properties;
    this.json = json;
  }

  @Override
  public boolean supports(String model) {
    return properties.enabled() && model.startsWith(PREFIX);
  }

  @Override
  public CachedResponse embed(byte[] requestBody) {
    JsonNode in = readTree(requestBody);
    String model = stripPrefix(in.path("model").asText(""));
    JsonNode input = in.path("input");
    boolean batch = input.isArray();
    byte[] geminiRequest = toGeminiRequest(model, input, batch);
    String uri =
        batch ? "/v1beta/models/{model}:batchEmbedContents" : "/v1beta/models/{model}:embedContent";
    try {
      return gemini
          .post()
          .uri(uri, model)
          .contentType(MediaType.APPLICATION_JSON)
          .body(geminiRequest)
          .exchange(
              (request, response) -> {
                int status = response.getStatusCode().value();
                String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                String openAiBody =
                    response.getStatusCode().is2xxSuccessful()
                        ? toOpenAiResponse(body, model, batch)
                        : body; // surface provider error bodies as-is
                return new CachedResponse(status, MediaType.APPLICATION_JSON_VALUE, openAiBody);
              });
    } catch (ResourceAccessException e) {
      throw new UpstreamUnavailableException("The Gemini provider is unavailable or timed out.", e);
    }
  }

  /** OpenAI embeddings input → Gemini {@code embedContent} / {@code batchEmbedContents} bytes. */
  private byte[] toGeminiRequest(String model, JsonNode input, boolean batch) {
    ObjectNode out = json.createObjectNode();
    if (batch) {
      ArrayNode requests = out.putArray("requests");
      for (JsonNode element : input) {
        requests.add(embedContent(model, element.asText()));
      }
    } else {
      ObjectNode single = embedContent(model, input.asText());
      out.setAll(single);
    }
    try {
      return json.writeValueAsBytes(out);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to translate embeddings request to Gemini", e);
    }
  }

  /** A single Gemini {@code EmbedContentRequest}: the model id plus the text content. */
  private ObjectNode embedContent(String model, String text) {
    ObjectNode request = json.createObjectNode();
    request.put("model", "models/" + model);
    ObjectNode content = request.putObject("content");
    ArrayNode parts = content.putArray("parts");
    parts.addObject().put("text", text);
    return request;
  }

  /** Gemini embeddings response → OpenAI embeddings response JSON. */
  private String toOpenAiResponse(String geminiBody, String model, boolean batch) {
    JsonNode in = readTreeString(geminiBody);
    ObjectNode out = json.createObjectNode();
    out.put("object", "list");
    ArrayNode data = out.putArray("data");
    if (batch) {
      int index = 0;
      for (JsonNode embedding : in.path("embeddings")) {
        data.add(dataEntry(embedding.path("values"), index++));
      }
    } else {
      data.add(dataEntry(in.path("embedding").path("values"), 0));
    }
    out.put("model", model);
    ObjectNode usage = out.putObject("usage");
    usage.put("prompt_tokens", 0);
    usage.put("total_tokens", 0);
    try {
      return json.writeValueAsString(out);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to translate Gemini embeddings response", e);
    }
  }

  /** One OpenAI {@code data[]} element wrapping the embedding vector at the given index. */
  private ObjectNode dataEntry(JsonNode values, int index) {
    ObjectNode entry = json.createObjectNode();
    entry.put("object", "embedding");
    entry.put("index", index);
    ArrayNode vector = entry.putArray("embedding");
    for (JsonNode value : values) {
      vector.add(value.doubleValue());
    }
    return entry;
  }

  private static String stripPrefix(String model) {
    return model.startsWith(PREFIX) ? model.substring(PREFIX.length()) : model;
  }

  private JsonNode readTree(byte[] requestBody) {
    try {
      JsonNode node = json.readTree(requestBody);
      if (node == null) {
        throw new IllegalStateException("Empty embeddings request for Gemini translation");
      }
      return node;
    } catch (IOException e) {
      throw new IllegalStateException(
          "Failed to read embeddings request for Gemini translation", e);
    }
  }

  private JsonNode readTreeString(String body) {
    try {
      JsonNode node = json.readTree(body);
      if (node == null) {
        throw new IllegalStateException("Empty Gemini embeddings response");
      }
      return node;
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read Gemini embeddings response", e);
    }
  }
}

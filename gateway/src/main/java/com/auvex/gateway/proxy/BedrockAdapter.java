package com.auvex.gateway.proxy;

import com.auvex.gateway.aws.Aws4Signer;
import com.auvex.gateway.aws.AwsCredentials;
import com.auvex.gateway.config.BedrockProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Amazon Bedrock provider adapter. Claims {@code bedrock/*} models when enabled, signs the request
 * with AWS SigV4 and calls the Bedrock Runtime {@code InvokeModel} API, translating to and from the
 * OpenAI shape via {@link BedrockTranslator} so everything downstream is provider-agnostic.
 *
 * <p>The model id after the {@code bedrock/} prefix becomes the Bedrock model id in the request
 * path; its reserved characters (notably the {@code :} in versioned ids) are percent-encoded, and
 * that exact encoded path is both signed and sent so the signature validates.
 */
@Component
@Order(0)
public class BedrockAdapter implements ProviderAdapter {

  private static final String PREFIX = "bedrock/";
  private static final String SERVICE = "bedrock";
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

  private final HttpClient http;
  private final BedrockTranslator translator;
  private final BedrockProperties properties;
  private final ObjectMapper json;

  public BedrockAdapter(
      HttpClient bedrockHttpClient,
      BedrockTranslator translator,
      BedrockProperties properties,
      ObjectMapper json) {
    this.http = bedrockHttpClient;
    this.translator = translator;
    this.properties = properties;
    this.json = json;
  }

  @Override
  public boolean supports(String model) {
    return properties.enabled() && model.startsWith(PREFIX);
  }

  @Override
  public CachedResponse fetch(byte[] requestBody) {
    AwsCredentials credentials =
        new AwsCredentials(
            properties.accessKeyId(), properties.secretAccessKey(), properties.sessionToken());
    if (!credentials.isComplete()) {
      throw new UpstreamUnavailableException(
          "Amazon Bedrock is enabled but AWS credentials are not configured.", null);
    }

    String modelId = modelIdOf(requestBody);
    URI uri =
        URI.create(
            "https://" + properties.host() + "/model/" + Aws4Signer.uriEncode(modelId) + "/invoke");
    byte[] bedrockBody = translator.toBedrockRequest(requestBody);

    Map<String, String> signed =
        Aws4Signer.signedHeaders(
            "POST", uri, bedrockBody, properties.region(), SERVICE, credentials, Instant.now());

    HttpRequest.Builder builder =
        HttpRequest.newBuilder(uri)
            .timeout(READ_TIMEOUT)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(bedrockBody));
    signed.forEach(builder::header);

    try {
      HttpResponse<String> response =
          http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      int status = response.statusCode();
      String openAiBody =
          status >= 200 && status < 300
              ? translator.toOpenAiResponse(response.body())
              : response.body(); // surface Bedrock error bodies as-is
      return new CachedResponse(status, "application/json", openAiBody);
    } catch (IOException e) {
      throw new UpstreamUnavailableException("Amazon Bedrock is unavailable or timed out.", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new UpstreamUnavailableException("Interrupted while calling Amazon Bedrock.", e);
    }
  }

  // The Bedrock model id is the resolved model with the bedrock/ prefix stripped.
  private String modelIdOf(byte[] requestBody) {
    try {
      JsonNode model = json.readTree(requestBody).path("model");
      String raw = model.asText("");
      return raw.startsWith(PREFIX) ? raw.substring(PREFIX.length()) : raw;
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read the model from the Bedrock request", e);
    }
  }
}

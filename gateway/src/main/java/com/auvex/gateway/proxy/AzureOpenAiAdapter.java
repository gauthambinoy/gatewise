package com.auvex.gateway.proxy;

import com.auvex.gateway.config.AzureProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Azure OpenAI's API. Claims {@code azure/*} models when enabled, forwarding to the deployment in
 * the path. Azure speaks the OpenAI shape, so there is no body translation — only the deployment is
 * lifted out of the model name and the body's {@code model} field is rewritten to it.
 */
@Component
@Order(1)
public class AzureOpenAiAdapter implements ProviderAdapter {

  private static final String PREFIX = "azure/";

  private final RestClient azure;
  private final AzureProperties properties;
  private final ObjectMapper json;

  public AzureOpenAiAdapter(
      RestClient azureRestClient, AzureProperties properties, ObjectMapper json) {
    this.azure = azureRestClient;
    this.properties = properties;
    this.json = json;
  }

  @Override
  public boolean supports(String model) {
    return properties.enabled() && model.startsWith(PREFIX);
  }

  @Override
  public CachedResponse fetch(byte[] requestBody) {
    String deployment;
    byte[] azureRequest;
    try {
      JsonNode in = json.readTree(requestBody);
      String model = in.path("model").asText("");
      deployment = model.startsWith(PREFIX) ? model.substring(PREFIX.length()) : model;
      // Azure takes the deployment in the path; the body's model must name the deployment too.
      ObjectNode out = (ObjectNode) in;
      out.put("model", deployment);
      azureRequest = json.writeValueAsBytes(out);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Failed to read request for Azure OpenAI", e);
    }

    try {
      return azure
          .post()
          .uri(
              "/openai/deployments/{deployment}/chat/completions?api-version={version}",
              deployment,
              properties.apiVersion())
          .contentType(MediaType.APPLICATION_JSON)
          .body(azureRequest)
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
          "The Azure OpenAI provider is unavailable or timed out.", e);
    }
  }
}

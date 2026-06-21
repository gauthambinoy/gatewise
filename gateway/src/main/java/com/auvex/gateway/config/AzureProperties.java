package com.auvex.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Native Azure OpenAI provider settings. When enabled, requests whose resolved model starts with
 * {@code azure/} are sent to your Azure OpenAI deployment. Azure speaks the same request/response
 * shape as OpenAI, so there is no body translation — only a different URL (the deployment lives in
 * the path) and auth (the {@code api-key} header).
 *
 * @param enabled route {@code azure/*} models straight to Azure OpenAI
 * @param endpoint Azure OpenAI resource endpoint (e.g. {@code
 *     https://my-resource.openai.azure.com})
 * @param apiKey Azure OpenAI API key (sent as {@code api-key})
 * @param apiVersion the {@code api-version} query parameter value
 */
@ConfigurationProperties(prefix = "auvex.azure")
public record AzureProperties(boolean enabled, String endpoint, String apiKey, String apiVersion) {

  public AzureProperties {
    if (apiVersion == null || apiVersion.isBlank()) {
      apiVersion = "2024-06-01";
    }
  }
}

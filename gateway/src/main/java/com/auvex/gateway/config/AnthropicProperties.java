package com.auvex.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Native Anthropic provider settings. When enabled, requests whose resolved model starts with
 * {@code anthropic/} are sent directly to Anthropic's {@code /v1/messages} API (translated to and
 * from the OpenAI shape), instead of through the OpenAI-compatible primary.
 *
 * @param enabled route {@code anthropic/*} models straight to Anthropic
 * @param baseUrl Anthropic API base URL
 * @param apiKey Anthropic API key (sent as {@code x-api-key})
 * @param version the {@code anthropic-version} header value
 * @param maxTokens default {@code max_tokens} when the caller omits it (Anthropic requires it)
 */
@ConfigurationProperties(prefix = "auvex.anthropic")
public record AnthropicProperties(
    boolean enabled, String baseUrl, String apiKey, String version, Integer maxTokens) {

  public AnthropicProperties {
    if (baseUrl == null || baseUrl.isBlank()) {
      baseUrl = "https://api.anthropic.com";
    }
    if (version == null || version.isBlank()) {
      version = "2023-06-01";
    }
    if (maxTokens == null || maxTokens <= 0) {
      maxTokens = 1024;
    }
  }
}

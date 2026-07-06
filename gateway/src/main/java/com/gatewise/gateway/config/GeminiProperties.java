package com.gatewise.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Native Google Gemini provider settings. When enabled, requests whose resolved model starts with
 * {@code google/} are sent directly to Gemini's {@code generateContent} API (translated to and from
 * the OpenAI shape), instead of through the OpenAI-compatible primary.
 *
 * @param enabled route {@code google/*} models straight to Gemini
 * @param baseUrl Gemini API base URL
 * @param apiKey Gemini API key (sent as {@code x-goog-api-key})
 * @param maxTokens default {@code maxOutputTokens} when the caller omits {@code max_tokens}
 */
@ConfigurationProperties(prefix = "gatewise.gemini")
public record GeminiProperties(boolean enabled, String baseUrl, String apiKey, Integer maxTokens) {

  public GeminiProperties {
    if (baseUrl == null || baseUrl.isBlank()) {
      baseUrl = "https://generativelanguage.googleapis.com";
    }
    if (maxTokens == null || maxTokens <= 0) {
      maxTokens = 1024;
    }
  }
}

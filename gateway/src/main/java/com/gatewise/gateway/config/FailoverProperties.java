package com.gatewise.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for a fallback model provider, tried when the primary fails.
 *
 * @param enabled whether failover is attempted at all
 * @param baseUrl the fallback provider's OpenAI-compatible base URL
 * @param apiKey the fallback provider's API key
 */
@ConfigurationProperties(prefix = "gatewise.failover")
public record FailoverProperties(boolean enabled, String baseUrl, String apiKey) {}

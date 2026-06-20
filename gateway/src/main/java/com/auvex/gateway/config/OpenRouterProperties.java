package com.auvex.gateway.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Strongly-typed configuration for the upstream LLM provider (OpenRouter, which speaks the
 * OpenAI-compatible API).
 *
 * <p>Every field is required. The {@code @NotBlank} on {@code apiKey} is the important one: it
 * means the gateway refuses to start when no key is supplied, failing fast with a clear message
 * instead of booting into a state where the first real request would blow up. Values come from the
 * environment (see application.yml) and are never hard-coded.
 *
 * @param baseUrl OpenAI-compatible base URL that requests are forwarded to
 * @param model default model alias used when a request doesn't pin one
 * @param apiKey bearer token for the provider — required, no default
 */
@Validated
@ConfigurationProperties(prefix = "auvex.openrouter")
public record OpenRouterProperties(
    @NotBlank String baseUrl, @NotBlank String model, @NotBlank String apiKey) {}

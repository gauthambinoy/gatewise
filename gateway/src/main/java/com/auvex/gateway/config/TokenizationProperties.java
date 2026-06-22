package com.auvex.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Reversible tokenization.
 *
 * @param enabled mask the prompt's sensitive values with per-value tokens (the provider never sees
 *     them) and restore them in the response for the caller. Off by default — redaction is plain
 *     and one-way unless this is on.
 */
@ConfigurationProperties(prefix = "auvex.tokenization")
public record TokenizationProperties(boolean enabled) {}

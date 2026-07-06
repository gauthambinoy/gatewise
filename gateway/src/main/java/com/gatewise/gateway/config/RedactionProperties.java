package com.gatewise.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Response-redaction settings. The prompt is always redacted; this governs the response.
 *
 * @param enabled redact PII out of the provider's response for the audit record (default true)
 * @param enforce also strip that PII from what the caller receives (default false — audit-only)
 */
@ConfigurationProperties(prefix = "gatewise.redaction.response")
public record RedactionProperties(Boolean enabled, boolean enforce) {

  public RedactionProperties {
    if (enabled == null) {
      enabled = Boolean.TRUE;
    }
  }
}

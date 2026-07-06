package com.gatewise.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Prompt-injection screening.
 *
 * @param enabled scan prompts for injection / jailbreak attempts (default true)
 * @param block reject a matching request with 403 (default false — detect-and-allow)
 */
@ConfigurationProperties(prefix = "gatewise.injection")
public record InjectionProperties(Boolean enabled, boolean block) {

  public InjectionProperties {
    if (enabled == null) {
      enabled = Boolean.TRUE;
    }
  }
}

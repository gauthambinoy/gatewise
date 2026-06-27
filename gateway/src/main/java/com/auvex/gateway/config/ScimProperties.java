package com.auvex.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SCIM 2.0 provisioning settings.
 *
 * <p>SCIM lets an identity provider create, update and deactivate console members automatically. It
 * authenticates with its own long-lived bearer {@code token} — deliberately separate from the
 * machine API keys that guard {@code /v1} — and provisions into a single configured tenant. SCIM is
 * off until a token is set (an empty token rejects every request), so leaving these blank is safe.
 *
 * @param token the bearer token an IdP presents on every SCIM call (the credentials-gated blank)
 * @param tenantSlug the tenant SCIM provisions members into
 * @param defaultRole the role given to a member SCIM creates (defaults to {@code auditor})
 */
@ConfigurationProperties(prefix = "auvex.scim")
public record ScimProperties(String token, String tenantSlug, String defaultRole) {

  public ScimProperties {
    if (defaultRole == null || defaultRole.isBlank()) {
      defaultRole = "auditor";
    }
  }

  /** True once a token is configured; until then SCIM rejects everything. */
  public boolean enabled() {
    return token != null && !token.isBlank();
  }
}

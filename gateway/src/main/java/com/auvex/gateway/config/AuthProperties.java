package com.auvex.gateway.config;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Console authentication settings.
 *
 * @param sessionSecret HMAC key for signing console session tokens (set a real one in production)
 * @param sessionTtl how long a console session is valid
 * @param devLoginEnabled enables the password-less dev login (off in production; SSO replaces it)
 * @param sso OIDC providers (e.g. google, okta); each is "configured" once its client id is set
 */
@ConfigurationProperties(prefix = "auvex.auth")
public record AuthProperties(
    String sessionSecret,
    Duration sessionTtl,
    boolean devLoginEnabled,
    Map<String, SsoProvider> sso) {

  public AuthProperties {
    if (sessionSecret == null || sessionSecret.isBlank()) {
      sessionSecret = "dev-insecure-session-secret-change-me-please-0123456789";
    }
    if (sessionTtl == null) {
      sessionTtl = Duration.ofHours(8);
    }
    sso = sso == null ? Map.of() : Map.copyOf(sso);
  }

  /** One OIDC provider's settings. Inert until a client id (and the OAuth wiring) is supplied. */
  public record SsoProvider(String issuer, String clientId) {}
}

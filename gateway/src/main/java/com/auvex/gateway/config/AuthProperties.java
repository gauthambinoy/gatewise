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

  /**
   * One OIDC provider's settings for the Authorization Code flow.
   *
   * <p>For Google (issuer {@code https://accounts.google.com}) the authorization, token and JWKS
   * endpoints are derived automatically; other providers supply them explicitly. The provider is
   * "configured" — and login works — once the client id/secret, endpoints, redirect URI and target
   * tenant are all present. Everything else has a safe default.
   *
   * @param issuer the OIDC issuer (used to validate the id_token {@code iss} and derive endpoints)
   * @param clientId OAuth client id
   * @param clientSecret OAuth client secret (kept server-side; used in the token exchange)
   * @param authorizationUri provider authorization endpoint (auto-derived for Google)
   * @param tokenUri provider token endpoint (auto-derived for Google)
   * @param jwksUri provider JWKS endpoint for id_token signature keys (auto-derived for Google)
   * @param redirectUri this gateway's callback URL registered with the provider
   * @param postLoginRedirect where to send the browser after a successful login (console URL)
   * @param scopes space-separated OAuth scopes (defaults to {@code openid email profile})
   * @param tenantSlug the tenant this provider signs users into
   * @param autoProvision create a member on first login if one doesn't exist
   * @param defaultRole the role given to an auto-provisioned member (defaults to {@code auditor})
   */
  public record SsoProvider(
      String issuer,
      String clientId,
      String clientSecret,
      String authorizationUri,
      String tokenUri,
      String jwksUri,
      String redirectUri,
      String postLoginRedirect,
      String scopes,
      String tenantSlug,
      boolean autoProvision,
      String defaultRole) {

    private static final String GOOGLE_ISSUER = "https://accounts.google.com";

    public SsoProvider {
      if (isBlank(scopes)) {
        scopes = "openid email profile";
      }
      if (isBlank(defaultRole)) {
        defaultRole = "auditor";
      }
      if (isBlank(postLoginRedirect)) {
        postLoginRedirect = "/";
      }
      String iss = issuer == null ? "" : issuer.replaceAll("/+$", "");
      if (GOOGLE_ISSUER.equals(iss)) {
        if (isBlank(authorizationUri)) {
          authorizationUri = "https://accounts.google.com/o/oauth2/v2/auth";
        }
        if (isBlank(tokenUri)) {
          tokenUri = "https://oauth2.googleapis.com/token";
        }
        if (isBlank(jwksUri)) {
          jwksUri = "https://www.googleapis.com/oauth2/v3/certs";
        }
      }
    }

    /** True once everything needed to actually run the login flow is present. */
    public boolean configured() {
      return !isBlank(clientId)
          && !isBlank(clientSecret)
          && !isBlank(authorizationUri)
          && !isBlank(tokenUri)
          && !isBlank(jwksUri)
          && !isBlank(redirectUri)
          && !isBlank(tenantSlug);
    }

    private static boolean isBlank(String s) {
      return s == null || s.isBlank();
    }
  }
}

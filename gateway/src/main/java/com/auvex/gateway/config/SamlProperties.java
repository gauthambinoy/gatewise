package com.auvex.gateway.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SAML 2.0 single-sign-on settings, one entry per identity provider (Okta, Entra ID, ADFS, …).
 *
 * <p>SAML is SP-initiated: {@code /auth/saml/&lt;name&gt;/login} sends the browser to the IdP, and
 * the IdP posts a signed assertion back to our ACS. Everything is wired and tested; the only blank
 * that needs real credentials is {@code signingCertificate} — the IdP's signing certificate, which
 * you paste in once the IdP application exists.
 *
 * @param providers the configured identity providers, keyed by the name used in the URL path
 */
@ConfigurationProperties(prefix = "auvex.saml")
public record SamlProperties(Map<String, SamlIdp> providers) {

  public SamlProperties {
    providers = providers == null ? Map.of() : Map.copyOf(providers);
  }

  /**
   * One identity provider's settings.
   *
   * @param entityId the IdP's entity id (its {@code Issuer}); we check every assertion came from it
   * @param ssoUrl the IdP's single-sign-on endpoint we redirect the browser to (HTTP-Redirect)
   * @param signingCertificate the IdP's signing certificate as PEM; assertions are verified against
   *     it (the credentials-gated blank — leave empty until the IdP application exists)
   * @param spEntityId our service-provider entity id; it must be the assertion's {@code Audience}
   * @param acsUrl our Assertion Consumer Service URL the IdP posts the response to
   * @param tenantSlug the tenant this provider signs users into
   * @param postLoginRedirect where to send the browser after a successful login (console URL)
   * @param autoProvision create a member on first login if one doesn't exist
   * @param defaultRole the role given to an auto-provisioned member (defaults to {@code auditor})
   */
  public record SamlIdp(
      String entityId,
      String ssoUrl,
      String signingCertificate,
      String spEntityId,
      String acsUrl,
      String tenantSlug,
      String postLoginRedirect,
      boolean autoProvision,
      String defaultRole) {

    public SamlIdp {
      if (isBlank(defaultRole)) {
        defaultRole = "auditor";
      }
      if (isBlank(postLoginRedirect)) {
        postLoginRedirect = "/";
      }
    }

    /** Enough to publish our SP metadata (our entity id + ACS URL). */
    public boolean metadataReady() {
      return !isBlank(spEntityId) && !isBlank(acsUrl);
    }

    /** Enough to start a login: where to send the user and how to identify ourselves. */
    public boolean loginReady() {
      return !isBlank(ssoUrl) && !isBlank(spEntityId) && !isBlank(acsUrl);
    }

    /** Fully configured: we can also verify what the IdP posts back (needs the signing cert). */
    public boolean configured() {
      return loginReady()
          && !isBlank(entityId)
          && !isBlank(signingCertificate)
          && !isBlank(tenantSlug);
    }

    private static boolean isBlank(String s) {
      return s == null || s.isBlank();
    }
  }
}

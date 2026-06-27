package com.auvex.gateway.saml;

import com.auvex.gateway.auth.ConsoleSession;
import com.auvex.gateway.auth.ConsoleSessionService;
import com.auvex.gateway.config.AuthProperties;
import com.auvex.gateway.config.SamlProperties;
import com.auvex.gateway.config.SamlProperties.SamlIdp;
import com.auvex.gateway.member.Member;
import com.auvex.gateway.member.MemberRepository;
import com.auvex.gateway.tenant.Tenant;
import com.auvex.gateway.tenant.TenantRepository;
import com.auvex.gateway.web.NotFoundException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * SP-initiated SAML 2.0 sign-in for the console — the SAML counterpart of {@link
 * com.auvex.gateway.web.OidcController}.
 *
 * <p>{@code /metadata} publishes our SP metadata for the IdP to import; {@code /login} redirects
 * the browser to the IdP with a signed RelayState; {@code /acs} receives the IdP's POST, verifies
 * the signed assertion ({@link SamlResponseValidator}), resolves (or provisions) the member and
 * mints the same {@link ConsoleSession} token every other login path produces. Everything is wired
 * and tested — the only blank is the IdP's signing certificate, supplied once the IdP app exists.
 */
@RestController
@RequestMapping("/auth/saml")
public class SamlController {

  private static final long RELAY_STATE_TTL_SECONDS = 600; // the round-trip must finish in 10 min
  private static final String EMAIL_FORMAT =
      "urn:oasis:names:tc:SAML:2.0:nameid-format:emailAddress";

  private final SamlProperties properties;
  private final AuthnRequestBuilder authnRequests;
  private final SamlRelayStateService relayStates;
  private final SamlResponseValidator validator;
  private final ConsoleSessionService sessions;
  private final AuthProperties authProperties;
  private final TenantRepository tenants;
  private final MemberRepository members;

  public SamlController(
      SamlProperties properties,
      AuthnRequestBuilder authnRequests,
      SamlRelayStateService relayStates,
      SamlResponseValidator validator,
      ConsoleSessionService sessions,
      AuthProperties authProperties,
      TenantRepository tenants,
      MemberRepository members) {
    this.properties = properties;
    this.authnRequests = authnRequests;
    this.relayStates = relayStates;
    this.validator = validator;
    this.sessions = sessions;
    this.authProperties = authProperties;
    this.tenants = tenants;
    this.members = members;
  }

  /** Publishes the SP metadata XML for the operator to upload into the IdP. */
  @GetMapping(value = "/{provider}/metadata", produces = MediaType.APPLICATION_XML_VALUE)
  public String metadata(@PathVariable String provider) {
    SamlIdp idp = require(provider, providerMetadataReady(provider), provider);
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        + "<EntityDescriptor xmlns=\"urn:oasis:names:tc:SAML:2.0:metadata\""
        + " entityID=\""
        + xml(idp.spEntityId())
        + "\">"
        + "<SPSSODescriptor AuthnRequestsSigned=\"false\" WantAssertionsSigned=\"true\""
        + " protocolSupportEnumeration=\"urn:oasis:names:tc:SAML:2.0:protocol\">"
        + "<NameIDFormat>"
        + EMAIL_FORMAT
        + "</NameIDFormat>"
        + "<AssertionConsumerService"
        + " Binding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST\""
        + " Location=\""
        + xml(idp.acsUrl())
        + "\" index=\"0\" isDefault=\"true\"/>"
        + "</SPSSODescriptor></EntityDescriptor>";
  }

  /**
   * Starts the flow: redirect to the IdP's SSO endpoint with an AuthnRequest + signed RelayState.
   */
  @GetMapping("/{provider}/login")
  public ResponseEntity<Void> login(@PathVariable String provider) {
    SamlIdp idp = require(provider, providerLoginReady(provider), provider);
    String requestId = relayStates.newRequestId();
    String relayState = relayStates.mint(provider, requestId, RELAY_STATE_TTL_SECONDS);
    String url = authnRequests.redirectUrl(idp, requestId, relayState);
    return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
  }

  /** Finishes the flow: verify the assertion, resolve the user, redirect with a session token. */
  @PostMapping("/{provider}/acs")
  public ResponseEntity<Void> acs(
      @PathVariable String provider,
      @RequestParam("SAMLResponse") String samlResponse,
      @RequestParam(value = "RelayState", required = false) String relayState) {

    SamlIdp idp = require(provider, providerConfigured(provider), provider);

    // SP-initiated logins carry our signed RelayState; bind the assertion to that exact request.
    String expectedRequestId = null;
    if (relayState != null && !relayState.isBlank()) {
      SamlRelayStateService.RelayState verified = relayStates.verify(relayState);
      if (!provider.equals(verified.provider())) {
        throw new SamlException("SAML RelayState does not match the provider.");
      }
      expectedRequestId = verified.requestId();
    }

    SamlAssertion assertion = validator.validate(samlResponse, idp, expectedRequestId);

    Tenant tenant =
        tenants
            .findBySlug(idp.tenantSlug())
            .orElseThrow(
                () -> new SamlException("The tenant configured for this provider does not exist."));
    Member member = resolveMember(tenant, assertion, idp);

    Instant expiresAt = Instant.now().plus(authProperties.sessionTtl());
    String token =
        sessions.mint(
            new ConsoleSession(
                tenant.getId(), member.getId(), member.getEmail(), member.getRole(), expiresAt));

    // Session goes in the URL fragment so it isn't sent to servers, logged, or leaked as referer.
    return redirect(idp.postLoginRedirect() + "#token=" + enc(token));
  }

  private Member resolveMember(Tenant tenant, SamlAssertion assertion, SamlIdp idp) {
    return members
        .findByTenantIdAndEmail(tenant.getId(), assertion.email())
        .orElseGet(
            () -> {
              if (!idp.autoProvision()) {
                throw new SamlException(
                    "No member for " + assertion.email() + " and auto-provisioning is off.");
              }
              String name = assertion.name().isBlank() ? assertion.email() : assertion.name();
              return members.save(
                  new Member(tenant.getId(), assertion.email(), name, idp.defaultRole(), "active"));
            });
  }

  private boolean providerMetadataReady(String provider) {
    SamlIdp idp = properties.providers().get(provider);
    return idp != null && idp.metadataReady();
  }

  private boolean providerLoginReady(String provider) {
    SamlIdp idp = properties.providers().get(provider);
    return idp != null && idp.loginReady();
  }

  private boolean providerConfigured(String provider) {
    SamlIdp idp = properties.providers().get(provider);
    return idp != null && idp.configured();
  }

  private SamlIdp require(String provider, boolean ready, String name) {
    SamlIdp idp = properties.providers().get(provider);
    if (idp == null || !ready) {
      throw new NotFoundException("Unknown or unconfigured SAML provider: " + name);
    }
    return idp;
  }

  private ResponseEntity<Void> redirect(String url) {
    return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
  }

  private static String enc(String value) {
    return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
  }

  private static String xml(String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}

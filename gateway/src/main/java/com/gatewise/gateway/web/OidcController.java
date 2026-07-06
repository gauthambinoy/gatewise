package com.gatewise.gateway.web;

import com.gatewise.gateway.auth.ConsoleSession;
import com.gatewise.gateway.auth.ConsoleSessionService;
import com.gatewise.gateway.config.AuthProperties;
import com.gatewise.gateway.config.AuthProperties.SsoProvider;
import com.gatewise.gateway.member.Member;
import com.gatewise.gateway.member.MemberRepository;
import com.gatewise.gateway.oidc.JwtVerifier;
import com.gatewise.gateway.oidc.OidcException;
import com.gatewise.gateway.oidc.OidcStateService;
import com.gatewise.gateway.oidc.OidcTokenClient;
import com.gatewise.gateway.oidc.OidcUser;
import com.gatewise.gateway.tenant.Tenant;
import com.gatewise.gateway.tenant.TenantRepository;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Real OIDC sign-in (Authorization Code flow) for the console — the production replacement for the
 * dev-login shortcut. {@code /login} redirects the browser to the provider; {@code /callback}
 * exchanges the code, verifies the id_token, resolves (or provisions) the member, and mints the
 * same {@link ConsoleSession} token every other login path produces.
 *
 * <p>Everything except the operator's OAuth client id/secret (and the provider endpoints, which are
 * auto-derived for Google) is wired and tested; supply credentials to switch a provider on.
 */
@RestController
@RequestMapping("/auth/oidc")
public class OidcController {

  private static final long STATE_TTL_SECONDS = 600; // the login round-trip must complete in 10 min

  private final AuthProperties properties;
  private final OidcStateService stateService;
  private final OidcTokenClient tokenClient;
  private final JwtVerifier jwtVerifier;
  private final ConsoleSessionService sessions;
  private final TenantRepository tenants;
  private final MemberRepository members;

  public OidcController(
      AuthProperties properties,
      OidcStateService stateService,
      OidcTokenClient tokenClient,
      JwtVerifier jwtVerifier,
      ConsoleSessionService sessions,
      TenantRepository tenants,
      MemberRepository members) {
    this.properties = properties;
    this.stateService = stateService;
    this.tokenClient = tokenClient;
    this.jwtVerifier = jwtVerifier;
    this.sessions = sessions;
    this.tenants = tenants;
    this.members = members;
  }

  /** Starts the flow: redirect the browser to the provider's authorization endpoint. */
  @GetMapping("/{provider}/login")
  public ResponseEntity<Void> login(@PathVariable String provider) {
    SsoProvider config = requireConfigured(provider);
    String nonce = stateService.newNonce();
    String state = stateService.mint(provider, nonce, STATE_TTL_SECONDS);

    String authorizationUrl =
        config.authorizationUri()
            + "?response_type=code"
            + "&client_id="
            + enc(config.clientId())
            + "&redirect_uri="
            + enc(config.redirectUri())
            + "&scope="
            + enc(config.scopes())
            + "&state="
            + enc(state)
            + "&nonce="
            + enc(nonce);
    return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(authorizationUrl)).build();
  }

  /** Finishes the flow: validate, resolve the user, and redirect to the console with a session. */
  @GetMapping("/{provider}/callback")
  public ResponseEntity<Void> callback(
      @PathVariable String provider,
      @RequestParam(required = false) String code,
      @RequestParam(required = false) String state,
      @RequestParam(required = false) String error) {

    SsoProvider config = requireConfigured(provider);
    if (error != null && !error.isBlank()) {
      return redirect(config.postLoginRedirect() + "?error=" + enc(error));
    }
    if (code == null || code.isBlank()) {
      return redirect(config.postLoginRedirect() + "?error=missing_code");
    }

    OidcStateService.State verifiedState = stateService.verify(state);
    if (!provider.equals(verifiedState.provider())) {
      throw new OidcException("OAuth state does not match the provider.");
    }

    String idToken = tokenClient.exchangeForIdToken(config, code);
    OidcUser user = jwtVerifier.verify(idToken, config, verifiedState.nonce());

    Tenant tenant =
        tenants
            .findBySlug(config.tenantSlug())
            .orElseThrow(
                () -> new OidcException("The tenant configured for this provider does not exist."));
    Member member = resolveMember(tenant, user, config);

    Instant expiresAt = Instant.now().plus(properties.sessionTtl());
    String token =
        sessions.mint(
            new ConsoleSession(
                tenant.getId(), member.getId(), member.getEmail(), member.getRole(), expiresAt));

    // The session goes in the URL fragment so it isn't sent to servers, logged, or sent as referer.
    return redirect(config.postLoginRedirect() + "#token=" + enc(token));
  }

  private Member resolveMember(Tenant tenant, OidcUser user, SsoProvider config) {
    return members
        .findByTenantIdAndEmail(tenant.getId(), user.email())
        .orElseGet(
            () -> {
              if (!config.autoProvision()) {
                throw new OidcException(
                    "No member for " + user.email() + " and auto-provisioning is off.");
              }
              String name = user.name().isBlank() ? user.email() : user.name();
              return members.save(
                  new Member(tenant.getId(), user.email(), name, config.defaultRole(), "active"));
            });
  }

  private SsoProvider requireConfigured(String provider) {
    SsoProvider config = properties.sso().get(provider);
    if (config == null || !config.configured()) {
      throw new NotFoundException("Unknown or unconfigured SSO provider: " + provider);
    }
    return config;
  }

  private ResponseEntity<Void> redirect(String url) {
    return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
  }

  private static String enc(String value) {
    return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
  }
}

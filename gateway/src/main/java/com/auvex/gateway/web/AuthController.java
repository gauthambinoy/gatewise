package com.auvex.gateway.web;

import com.auvex.gateway.auth.ConsoleSession;
import com.auvex.gateway.auth.ConsoleSessionService;
import com.auvex.gateway.config.AuthProperties;
import com.auvex.gateway.member.Member;
import com.auvex.gateway.member.MemberRepository;
import com.auvex.gateway.tenant.Tenant;
import com.auvex.gateway.tenant.TenantRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Console authentication.
 *
 * <p>These live under {@code /auth} (not {@code /v1}), so they're outside the machine API-key
 * filter — they authenticate a human, not a service. {@code /dev-login} is a password-less shortcut
 * for local development and is disabled by default; in production a real OIDC sign-in (Google/Okta)
 * mints the same session token. {@code /session} resolves the current member from that token.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

  private static final String BEARER = "Bearer ";

  private final AuthProperties properties;
  private final com.auvex.gateway.config.DemoProperties demoProperties;
  private final ConsoleSessionService sessions;
  private final TenantRepository tenants;
  private final MemberRepository members;

  public AuthController(
      AuthProperties properties,
      com.auvex.gateway.config.DemoProperties demoProperties,
      ConsoleSessionService sessions,
      TenantRepository tenants,
      MemberRepository members) {
    this.properties = properties;
    this.demoProperties = demoProperties;
    this.sessions = sessions;
    this.tenants = tenants;
    this.members = members;
  }

  /**
   * Demo-only: mints a console session for the seeded demo tenant's owner, so the "Try the live
   * demo" button works under RBAC without a real login. Enabled only when the demo seed is on.
   */
  @PostMapping("/demo-login")
  public DevLoginResponse demoLogin() {
    if (!demoProperties.enabled()) {
      throw new NotFoundException("Demo login is disabled.");
    }
    Tenant tenant =
        tenants
            .findBySlug("demo")
            .orElseThrow(() -> new NotFoundException("Demo tenant is not seeded."));
    Member owner = members.findFirstByTenantIdAndRole(tenant.getId(), "owner").orElse(null);
    String email = owner != null ? owner.getEmail() : "demo@auvex.io";
    java.util.UUID memberId = owner != null ? owner.getId() : null;
    Instant expiresAt = Instant.now().plus(properties.sessionTtl());
    ConsoleSession session =
        new ConsoleSession(tenant.getId(), memberId, email, "owner", expiresAt);
    return new DevLoginResponse(sessions.mint(session), email, "owner", expiresAt);
  }

  /** Dev-only: mints a console session for a member, without a password (off in production). */
  @PostMapping("/dev-login")
  public DevLoginResponse devLogin(@Valid @RequestBody DevLoginRequest request) {
    if (!properties.devLoginEnabled()) {
      throw new NotFoundException("Dev login is disabled.");
    }
    Tenant tenant =
        tenants
            .findBySlug(request.tenantSlug())
            .orElseThrow(() -> new InvalidRequestException("Unknown tenant."));
    Member member =
        members
            .findByTenantIdAndEmail(tenant.getId(), request.email())
            .orElseThrow(() -> new InvalidRequestException("No such member for this tenant."));
    Instant expiresAt = Instant.now().plus(properties.sessionTtl());
    ConsoleSession session =
        new ConsoleSession(
            tenant.getId(), member.getId(), member.getEmail(), member.getRole(), expiresAt);
    return new DevLoginResponse(
        sessions.mint(session), member.getEmail(), member.getRole(), expiresAt);
  }

  /** Resolves the current console session from its bearer token, or 401. */
  @GetMapping("/session")
  public ConsoleSession session(
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    String token =
        authorization != null && authorization.startsWith(BEARER)
            ? authorization.substring(BEARER.length()).trim()
            : null;
    return sessions
        .verify(token)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Invalid or expired session."));
  }

  /** Lists the SSO providers and whether each is configured (has a client id). */
  @GetMapping("/providers")
  public List<ProviderView> providers() {
    return properties.sso().entrySet().stream()
        .map(entry -> new ProviderView(entry.getKey(), entry.getValue().configured()))
        .sorted(Comparator.comparing(ProviderView::name))
        .toList();
  }

  /** Dev-login body: which tenant (by slug) and which member (by email). */
  public record DevLoginRequest(@NotBlank String tenantSlug, @NotBlank @Email String email) {}

  /** Dev-login result: the session token and the member it represents. */
  public record DevLoginResponse(String token, String email, String role, Instant expiresAt) {}

  /** An SSO provider and whether it's wired up. */
  public record ProviderView(String name, boolean configured) {}
}

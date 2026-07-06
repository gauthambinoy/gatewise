package com.gatewise.gateway.scim;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatewise.gateway.auth.AuthenticatedTenant;
import com.gatewise.gateway.auth.TenantContext;
import com.gatewise.gateway.config.ScimProperties;
import com.gatewise.gateway.tenant.Tenant;
import com.gatewise.gateway.tenant.TenantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates the SCIM provisioning API with its own bearer token, separate from the machine API
 * keys that guard {@code /v1}.
 *
 * <p>SCIM lives under {@code /scim}, outside the API-key filter, so it brings its own door: a
 * single configured token (constant-time compared) that an IdP presents on every call. SCIM is off
 * until a token is set, in which case every request is refused. On success the configured tenant is
 * bound to the request, so the controller stays scoped to exactly that tenant. Errors use the SCIM
 * 2.0 error envelope rather than the gateway's OpenAI-style one.
 */
@Component
public class ScimAuthFilter extends OncePerRequestFilter {

  private static final String BEARER = "Bearer ";
  private static final List<String> ERROR_SCHEMAS =
      List.of("urn:ietf:params:scim:api:messages:2.0:Error");

  private final ScimProperties properties;
  private final TenantRepository tenants;
  private final ObjectMapper json;

  public ScimAuthFilter(ScimProperties properties, TenantRepository tenants, ObjectMapper json) {
    this.properties = properties;
    this.tenants = tenants;
    this.json = json;
  }

  // Engage only for the SCIM surface; everything else passes straight through.
  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/scim/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    if (!properties.enabled()) {
      error(response, HttpStatus.UNAUTHORIZED, "SCIM is not enabled on this gateway.");
      return;
    }
    String header = request.getHeader("Authorization");
    if (header == null || !header.startsWith(BEARER)) {
      error(response, HttpStatus.UNAUTHORIZED, "Missing or malformed SCIM bearer token.");
      return;
    }
    String presented = header.substring(BEARER.length()).trim();
    if (!constantTimeEquals(presented, properties.token())) {
      error(response, HttpStatus.UNAUTHORIZED, "Invalid SCIM bearer token.");
      return;
    }

    Optional<Tenant> tenant = tenants.findBySlug(properties.tenantSlug());
    if (tenant.isEmpty()) {
      error(
          response,
          HttpStatus.INTERNAL_SERVER_ERROR,
          "SCIM is misconfigured: the target tenant does not exist.");
      return;
    }

    TenantContext.set(new AuthenticatedTenant(tenant.get().getId(), null));
    try {
      chain.doFilter(request, response);
    } finally {
      TenantContext.clear();
    }
  }

  private static boolean constantTimeEquals(String a, String b) {
    if (a == null || b == null) {
      return false;
    }
    return MessageDigest.isEqual(
        a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }

  private void error(HttpServletResponse response, HttpStatus status, String detail)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType("application/scim+json");
    json.writeValue(
        response.getWriter(),
        Map.of(
            "schemas", ERROR_SCHEMAS,
            "detail", detail,
            "status", String.valueOf(status.value())));
  }
}

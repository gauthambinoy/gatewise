package com.auvex.gateway.auth;

import com.auvex.gateway.config.RbacProperties;
import com.auvex.gateway.ratelimit.RateLimiter;
import com.auvex.gateway.tenant.ApiKey;
import com.auvex.gateway.tenant.ApiKeyRepository;
import com.auvex.gateway.tenant.Tenant;
import com.auvex.gateway.tenant.TenantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates every {@code /v1} request by its API key.
 *
 * <p>The key arrives as {@code Authorization: Bearer <key>}. We hash it and look it up, rejecting
 * any missing, malformed, unknown, revoked or expired key — and any suspended tenant — with a clear
 * 401. On success the tenant is bound to the request via {@link TenantContext} so downstream code
 * stays isolated to that tenant.
 */
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER = "Bearer ";

  private final ApiKeyRepository apiKeys;
  private final TenantRepository tenants;
  private final ObjectMapper json;
  private final RateLimiter rateLimiter;
  private final RbacProperties rbac;

  public ApiKeyAuthenticationFilter(
      ApiKeyRepository apiKeys,
      TenantRepository tenants,
      ObjectMapper json,
      RateLimiter rateLimiter,
      RbacProperties rbac) {
    this.apiKeys = apiKeys;
    this.tenants = tenants;
    this.json = json;
    this.rateLimiter = rateLimiter;
    this.rbac = rbac;
  }

  // Guard the gateway API surface; health checks and other paths stay public. When RBAC is on, the
  // human-managed endpoints are handled by the console-session filter instead of by API key.
  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String uri = request.getRequestURI();
    if (!uri.startsWith("/v1/")) {
      return true;
    }
    if (uri.equals("/v1/egress/ca.pem")) {
      // The egress CA is public bootstrap material: a fresh client must fetch and trust it before
      // it has any credential to present, so this one endpoint stays open.
      return true;
    }
    return rbac.enabled() && ManagementAccess.isManagement(uri);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    String header = request.getHeader("Authorization");
    if (header == null || !header.startsWith(BEARER)) {
      unauthorized(
          response,
          "Missing or malformed Authorization header. Use 'Authorization: Bearer <api-key>'.");
      return;
    }
    String rawKey = header.substring(BEARER.length()).trim();
    if (rawKey.isEmpty()) {
      unauthorized(response, "Empty API key.");
      return;
    }

    Optional<ApiKey> match = apiKeys.findByKeyHash(ApiKeyHasher.hash(rawKey));
    if (match.isEmpty()) {
      unauthorized(response, "Invalid API key.");
      return;
    }
    ApiKey key = match.get();
    if (!key.isActive()) {
      unauthorized(response, "API key has been revoked.");
      return;
    }
    if (key.isExpired()) {
      unauthorized(response, "API key has expired.");
      return;
    }

    Optional<Tenant> tenant = tenants.findById(key.getTenantId());
    if (tenant.isEmpty() || !tenant.get().isActive()) {
      unauthorized(response, "Tenant is not active.");
      return;
    }

    if (!rateLimiter.allow(key.getTenantId())) {
      tooManyRequests(response, "Rate limit exceeded; too many requests this minute.");
      return;
    }

    TenantContext.set(new AuthenticatedTenant(key.getTenantId(), key.getId()));
    try {
      chain.doFilter(request, response);
    } finally {
      TenantContext.clear();
    }
  }

  // OpenAI-style error envelope so existing OpenAI-compatible clients can parse our 401s.
  private void unauthorized(HttpServletResponse response, String message) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType("application/json");
    json.writeValue(
        response.getWriter(),
        Map.of(
            "error",
            Map.of(
                "message", message, "type", "invalid_request_error", "code", "invalid_api_key")));
  }

  // Same OpenAI-style envelope, for a 429 when the tenant exceeds its rate limit.
  private void tooManyRequests(HttpServletResponse response, String message) throws IOException {
    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    response.setContentType("application/json");
    json.writeValue(
        response.getWriter(),
        Map.of("error", Map.of("message", message, "type", "rate_limit_exceeded")));
  }
}

package com.auvex.gateway.auth;

import com.auvex.gateway.config.RbacProperties;
import com.auvex.gateway.member.Role;
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
 * Authenticates and role-gates the console management API when RBAC is enabled.
 *
 * <p>Management endpoints are operated by people, so they require a console-session token (a human
 * login) rather than a machine API key. The session carries the member's role, which must meet the
 * minimum for the path + method (see {@link ManagementAccess}); otherwise the call is rejected with
 * 401 (no/invalid session) or 403 (insufficient role). When RBAC is off this filter is inert and
 * the API-key filter continues to guard the whole {@code /v1} surface.
 */
@Component
public class ConsoleAuthFilter extends OncePerRequestFilter {

  private static final String BEARER = "Bearer ";

  private final ConsoleSessionService sessions;
  private final RbacProperties rbac;
  private final ObjectMapper json;

  public ConsoleAuthFilter(ConsoleSessionService sessions, RbacProperties rbac, ObjectMapper json) {
    this.sessions = sessions;
    this.rbac = rbac;
    this.json = json;
  }

  // Only engage for management paths, and only when RBAC enforcement is switched on.
  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !rbac.enabled() || !ManagementAccess.isManagement(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    String header = request.getHeader("Authorization");
    if (header == null || !header.startsWith(BEARER)) {
      error(
          response,
          HttpStatus.UNAUTHORIZED,
          "Sign in required: this endpoint needs a console session.");
      return;
    }
    Optional<ConsoleSession> session = sessions.verify(header.substring(BEARER.length()).trim());
    if (session.isEmpty()) {
      error(response, HttpStatus.UNAUTHORIZED, "Invalid or expired console session.");
      return;
    }

    Role role = Role.from(session.get().role());
    Role required = ManagementAccess.requiredRole(request.getMethod(), request.getRequestURI());
    if (!role.atLeast(required)) {
      error(
          response,
          HttpStatus.FORBIDDEN,
          "Your role ("
              + role.value()
              + ") cannot perform this action; "
              + required.value()
              + " or higher is required.");
      return;
    }

    TenantContext.set(
        new AuthenticatedTenant(
            session.get().tenantId(), null, session.get().memberId(), session.get().email()));
    try {
      chain.doFilter(request, response);
    } finally {
      TenantContext.clear();
    }
  }

  private void error(HttpServletResponse response, HttpStatus status, String message)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType("application/json");
    String type = status == HttpStatus.FORBIDDEN ? "insufficient_role" : "authentication_error";
    json.writeValue(
        response.getWriter(), Map.of("error", Map.of("message", message, "type", type)));
  }
}

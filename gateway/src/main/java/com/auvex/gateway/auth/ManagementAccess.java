package com.auvex.gateway.auth;

import com.auvex.gateway.member.Role;

/**
 * Classifies {@code /v1} request paths into the human-managed "management" surface vs the machine
 * AI/proxy surface, and maps each management path + method to the minimum role required.
 *
 * <p>Management endpoints (policies, members, keys, approvals, and the read-only audit/usage/
 * discovery/compliance views) are operated by people and are gated by console-session role when
 * RBAC is enabled. The AI endpoints (chat/responses/embeddings/images/moderations/audio/mcp) and
 * the OpenAI-client conveniences ({@code /v1/models}, {@code /v1/me}) stay API-key authenticated.
 */
public final class ManagementAccess {

  private static final String[] MANAGEMENT_PREFIXES = {
    "/v1/policies",
    "/v1/members",
    "/v1/keys",
    "/v1/approvals",
    "/v1/audit",
    "/v1/usage",
    "/v1/discovery",
    "/v1/compliance"
  };

  private ManagementAccess() {}

  /** Whether the path is part of the human-managed console surface. */
  public static boolean isManagement(String uri) {
    for (String prefix : MANAGEMENT_PREFIXES) {
      if (uri.equals(prefix) || uri.startsWith(prefix + "/") || uri.startsWith(prefix + "?")) {
        return true;
      }
    }
    return false;
  }

  /** The least-privileged role allowed to make this management call. */
  public static Role requiredRole(String method, String uri) {
    boolean write = !"GET".equalsIgnoreCase(method);
    if (uri.startsWith("/v1/members")) {
      return write ? Role.OWNER : Role.AUDITOR;
    }
    if (uri.startsWith("/v1/keys")) {
      return Role.SECURITY_ADMIN; // keys are sensitive even to read
    }
    if (uri.startsWith("/v1/policies")) {
      return write ? Role.SECURITY_ADMIN : Role.AUDITOR;
    }
    if (uri.startsWith("/v1/approvals")) {
      return write ? Role.SECURITY_ADMIN : Role.AUDITOR;
    }
    // Read-only analytics surfaces: any signed-in role may view.
    return Role.AUDITOR;
  }
}

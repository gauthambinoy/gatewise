package com.auvex.gateway.auth;

import java.util.UUID;

/**
 * The principal bound to a request once it has been authenticated.
 *
 * <p>A programmatic call carries an {@code apiKeyId}; a human acting through the console carries a
 * {@code memberId} and {@code memberEmail}. The derived {@link #principalType()} / {@link
 * #principalId()} give audit a uniform way to record "who did this" regardless of which it is.
 */
public record AuthenticatedTenant(UUID tenantId, UUID apiKeyId, UUID memberId, String memberEmail) {

  /** A programmatic principal authenticated by API key, with no console member. */
  public AuthenticatedTenant(UUID tenantId, UUID apiKeyId) {
    this(tenantId, apiKeyId, null, null);
  }

  /** "member" when a human console member is acting, "api_key" for a programmatic call. */
  public String principalType() {
    if (memberId != null) {
      return "member";
    }
    if (apiKeyId != null) {
      return "api_key";
    }
    return "system";
  }

  /** The id of whoever is acting: the member if present, otherwise the API key. */
  public UUID principalId() {
    return memberId != null ? memberId : apiKeyId;
  }
}

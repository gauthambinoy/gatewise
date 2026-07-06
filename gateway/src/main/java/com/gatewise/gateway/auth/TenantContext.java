package com.gatewise.gateway.auth;

/**
 * Holds the authenticated tenant for the duration of a single request.
 *
 * <p>The auth filter sets it after verifying the API key and clears it when the request finishes,
 * so downstream code can resolve "who is calling" without re-reading the key — the basis for tenant
 * isolation.
 */
public final class TenantContext {

  private static final ThreadLocal<AuthenticatedTenant> CURRENT = new ThreadLocal<>();

  private TenantContext() {}

  /** Binds the authenticated tenant to the current request thread. */
  public static void set(AuthenticatedTenant tenant) {
    CURRENT.set(tenant);
  }

  /** Returns the bound tenant, or null if the request wasn't authenticated. */
  public static AuthenticatedTenant get() {
    return CURRENT.get();
  }

  /** Returns the bound tenant, or throws if none — use when auth is guaranteed. */
  public static AuthenticatedTenant require() {
    AuthenticatedTenant tenant = CURRENT.get();
    if (tenant == null) {
      throw new IllegalStateException("No authenticated tenant bound to this request");
    }
    return tenant;
  }

  /** Clears the binding; the filter always calls this in a finally block. */
  public static void clear() {
    CURRENT.remove();
  }
}

package com.gatewise.gateway.web;

import com.gatewise.gateway.auth.AuthenticatedTenant;
import com.gatewise.gateway.auth.TenantContext;
import com.gatewise.gateway.tenant.Tenant;
import com.gatewise.gateway.tenant.TenantRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets a caller confirm which tenant their API key resolves to.
 *
 * <p>By the time this runs, the auth filter has already verified the key and bound the tenant, so
 * we just read it back. It's a small but genuinely useful endpoint: clients verify their
 * credentials, and it gives the auth layer something concrete to exercise before the proxy lands.
 */
@RestController
@RequestMapping("/v1")
public class MeController {

  private final TenantRepository tenants;

  public MeController(TenantRepository tenants) {
    this.tenants = tenants;
  }

  /** Returns the tenant behind the presented API key. */
  @GetMapping("/me")
  public ResponseEntity<TenantView> me() {
    AuthenticatedTenant auth = TenantContext.require();
    Tenant tenant =
        tenants
            .findById(auth.tenantId())
            .orElseThrow(() -> new IllegalStateException("Authenticated tenant no longer exists"));
    return ResponseEntity.ok(new TenantView(tenant.getId(), tenant.getName(), tenant.getSlug()));
  }
}

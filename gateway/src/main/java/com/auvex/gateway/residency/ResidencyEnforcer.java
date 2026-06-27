package com.auvex.gateway.residency;

import com.auvex.gateway.config.ResidencyProperties;
import com.auvex.gateway.tenant.Tenant;
import com.auvex.gateway.tenant.TenantRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Enforces per-tenant data residency: a tenant pinned to a region may only use models that run in
 * that region.
 *
 * <p>Off unless {@code auvex.residency.enabled} is set, and a tenant with no pinned region is
 * always allowed — so existing tenants are unaffected and the feature is drop-in safe. When
 * enabled, the tenant's pinned region is read once per request (only on this guarded path).
 */
@Component
public class ResidencyEnforcer {

  private final ResidencyProperties properties;
  private final TenantRepository tenants;

  public ResidencyEnforcer(ResidencyProperties properties, TenantRepository tenants) {
    this.properties = properties;
    this.tenants = tenants;
  }

  /** Blocks the call when the tenant's pinned region doesn't permit the resolved provider model. */
  public void enforce(UUID tenantId, String providerModel) {
    if (!properties.enabled()) {
      return;
    }
    String pinned = tenants.findById(tenantId).map(Tenant::getResidencyRegion).orElse(null);
    check(pinned, providerModel);
  }

  /**
   * The pure decision: a blank pin allows anything; otherwise the model's configured region must
   * equal the pin (case-insensitively), and a model with no configured region is refused — failing
   * closed so an unmapped model can't leak a pinned tenant's data to an unknown region.
   */
  void check(String pinnedRegion, String providerModel) {
    if (pinnedRegion == null || pinnedRegion.isBlank()) {
      return;
    }
    String modelRegion = properties.regionFor(providerModel);
    if (modelRegion == null) {
      throw new DataResidencyException(
          "Model '"
              + providerModel
              + "' has no configured region and cannot be used by a tenant pinned to '"
              + pinnedRegion
              + "'.");
    }
    if (!pinnedRegion.equalsIgnoreCase(modelRegion)) {
      throw new DataResidencyException(
          "Model '"
              + providerModel
              + "' runs in region '"
              + modelRegion
              + "', which is not permitted for a tenant pinned to '"
              + pinnedRegion
              + "'.");
    }
  }
}

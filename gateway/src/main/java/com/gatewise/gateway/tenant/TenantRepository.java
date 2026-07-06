package com.gatewise.gateway.tenant;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for tenants. */
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

  /** Looks up a tenant by its url-safe slug (used by the console dev login). */
  Optional<Tenant> findBySlug(String slug);
}

package com.auvex.gateway.tenant;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for tenants. */
public interface TenantRepository extends JpaRepository<Tenant, UUID> {}

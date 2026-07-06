package com.gatewise.gateway.audit;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Stores and queries the console management-action trail, always scoped to one tenant. */
public interface ManagementAuditRepository extends JpaRepository<ManagementAudit, Long> {

  /** A page of one tenant's management actions (ordered by the caller's Pageable). */
  Page<ManagementAudit> findByTenantId(UUID tenantId, Pageable pageable);
}

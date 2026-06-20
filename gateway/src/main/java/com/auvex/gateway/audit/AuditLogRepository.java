package com.auvex.gateway.audit;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for audit-log rows. */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

  /** The current head of a tenant's chain (its most recent entry), if any. */
  Optional<AuditLog> findTopByTenantIdOrderByIdDesc(UUID tenantId);

  /** A tenant's chain oldest-first, for verification. */
  List<AuditLog> findByTenantIdOrderByIdAsc(UUID tenantId);
}

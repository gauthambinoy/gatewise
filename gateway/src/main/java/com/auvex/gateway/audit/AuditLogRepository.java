package com.auvex.gateway.audit;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for audit-log rows. */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

  /** The current head of a tenant's chain (its most recent entry), if any. */
  Optional<AuditLog> findTopByTenantIdOrderByIdDesc(UUID tenantId);

  /** A tenant's chain oldest-first, for verification. */
  List<AuditLog> findByTenantIdOrderByIdAsc(UUID tenantId);

  /** A page of a tenant's entries, for the query API. */
  Page<AuditLog> findByTenantId(UUID tenantId, Pageable pageable);

  /** A page of a tenant's entries with a given verdict. */
  Page<AuditLog> findByTenantIdAndVerdict(UUID tenantId, String verdict, Pageable pageable);
}

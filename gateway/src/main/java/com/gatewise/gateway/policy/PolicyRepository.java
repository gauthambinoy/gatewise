package com.gatewise.gateway.policy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for policy rules. */
public interface PolicyRepository extends JpaRepository<Policy, UUID> {

  /** The enabled rules for a tenant — the candidates the engine evaluates. */
  List<Policy> findByTenantIdAndEnabledTrue(UUID tenantId);

  /** All of a tenant's rules, for the management API. */
  List<Policy> findByTenantId(UUID tenantId);

  /** One rule, but only if it belongs to the given tenant (the isolation guard). */
  Optional<Policy> findByIdAndTenantId(UUID id, UUID tenantId);
}

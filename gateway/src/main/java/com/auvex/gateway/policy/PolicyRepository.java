package com.auvex.gateway.policy;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for policy rules. */
public interface PolicyRepository extends JpaRepository<Policy, UUID> {

  /** The enabled rules for a tenant — the candidates the engine evaluates. */
  List<Policy> findByTenantIdAndEnabledTrue(UUID tenantId);
}

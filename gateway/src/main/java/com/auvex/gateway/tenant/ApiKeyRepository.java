package com.auvex.gateway.tenant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for API keys, looked up by their stored hash during authentication. */
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

  /** Finds the key whose stored hash matches the presented (hashed) key, if any. */
  Optional<ApiKey> findByKeyHash(String keyHash);

  /** A tenant's keys, for the management list. */
  List<ApiKey> findByTenantIdOrderByCreatedAtAsc(UUID tenantId);

  /** One key, only if it belongs to the tenant. */
  Optional<ApiKey> findByIdAndTenantId(UUID id, UUID tenantId);
}

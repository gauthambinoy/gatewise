package com.auvex.gateway.tenant;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for API keys, looked up by their stored hash during authentication. */
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

  /** Finds the key whose stored hash matches the presented (hashed) key, if any. */
  Optional<ApiKey> findByKeyHash(String keyHash);
}

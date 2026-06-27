package com.auvex.gateway.audit;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The default (Postgres) {@link ChainLock}: a per-tenant, transaction-scoped advisory lock.
 *
 * <p>{@code pg_advisory_xact_lock} takes a lock keyed by a hash of the tenant id for the duration
 * of the transaction, so two concurrent appends for the same tenant are serialised (the second
 * waits) and can't both extend the same chain head; different tenants hash to different keys and
 * never contend. Active for every profile except {@code sqlite}.
 */
@Component
@Profile("!sqlite")
public class PostgresAdvisoryChainLock implements ChainLock {

  private final EntityManager entityManager;

  public PostgresAdvisoryChainLock(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Override
  public void lockForAppend(UUID tenantId) {
    entityManager
        .createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended(cast(:t as text), 42))")
        .setParameter("t", tenantId.toString())
        .getResultList();
  }
}

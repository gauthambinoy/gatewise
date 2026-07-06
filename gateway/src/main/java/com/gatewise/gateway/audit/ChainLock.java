package com.gatewise.gateway.audit;

import java.util.UUID;

/**
 * Serialises appends to a single tenant's audit hash chain, so two concurrent appends can't both
 * read the same chain head and fork the chain. Different tenants never contend.
 *
 * <p>The mechanism is backend-specific: on Postgres a per-tenant transaction-scoped advisory lock;
 * on the embedded SQLite single-binary backend a no-op, because SQLite already serialises writers
 * at the database level. Selecting the right implementation by profile is what lets the same audit
 * code run on either backend.
 */
public interface ChainLock {

  /**
   * Acquires the lock for {@code tenantId} for the remainder of the current transaction. Must be
   * called inside the append transaction; the lock is released when that transaction ends.
   */
  void lockForAppend(UUID tenantId);
}

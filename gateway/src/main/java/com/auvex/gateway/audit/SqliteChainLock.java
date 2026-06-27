package com.auvex.gateway.audit;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The SQLite {@link ChainLock}: a no-op.
 *
 * <p>SQLite permits only one writer at a time — a write transaction takes a database-level lock —
 * so appends are already fully serialised and two concurrent appends can never both read the same
 * chain head. The per-tenant advisory lock the Postgres backend needs is therefore unnecessary
 * here; the {@code UNIQUE (tenant_id, prev_hash)} anti-fork constraint still backs it up. Active
 * only under the {@code sqlite} single-binary profile.
 */
@Component
@Profile("sqlite")
public class SqliteChainLock implements ChainLock {

  @Override
  public void lockForAppend(UUID tenantId) {
    // No-op: SQLite serialises writers at the database level (see class Javadoc).
  }
}

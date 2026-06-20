package com.auvex.gateway.audit;

import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Appends entries to a tenant's hash chain and verifies it.
 *
 * <p>Each append takes a per-tenant Postgres advisory lock for the duration of its transaction, so
 * two concurrent appends for the same tenant can't both read the same chain head and fork the
 * chain; different tenants never contend. The write is synchronous (an async pipeline is a later
 * step) but is just a couple of indexed statements.
 */
@Component
public class AuditService {

  private final AuditLogRepository repository;
  private final EntityManager entityManager;

  public AuditService(AuditLogRepository repository, EntityManager entityManager) {
    this.repository = repository;
    this.entityManager = entityManager;
  }

  /** Appends an entry to its tenant's chain, computing and storing the link hashes. */
  @Transactional
  public AuditLog append(AuditEntry entry) {
    lockChain(entry.tenantId());
    String prevHash =
        repository
            .findTopByTenantIdOrderByIdDesc(entry.tenantId())
            .map(AuditLog::getEntryHash)
            .orElse(AuditChain.GENESIS);
    String entryHash = AuditChain.entryHash(prevHash, entry);
    OffsetDateTime createdAt =
        entry.createdAt().truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
    AuditLog row =
        new AuditLog(
            entry.tenantId(),
            entry.requestId(),
            entry.actor(),
            entry.model(),
            entry.verdict().value(),
            entry.promptRedacted(),
            entry.responseRedacted(),
            prevHash,
            entryHash,
            createdAt);
    return repository.save(row);
  }

  /**
   * Walks a tenant's chain oldest-first and returns the id of the first broken link, or empty if
   * the chain is intact. A broken link is a prev_hash that doesn't continue the chain, or an entry
   * whose stored hash doesn't match a fresh recomputation (i.e. a tampered field).
   */
  @Transactional(readOnly = true)
  public Optional<Long> firstBrokenLink(UUID tenantId) {
    String expectedPrev = AuditChain.GENESIS;
    for (AuditLog row : repository.findByTenantIdOrderByIdAsc(tenantId)) {
      if (!expectedPrev.equals(row.getPrevHash())) {
        return Optional.of(row.getId());
      }
      if (!AuditChain.entryHash(row.getPrevHash(), toEntry(row)).equals(row.getEntryHash())) {
        return Optional.of(row.getId());
      }
      expectedPrev = row.getEntryHash();
    }
    return Optional.empty();
  }

  // Serialize this tenant's appends; the lock auto-releases when the transaction ends.
  private void lockChain(UUID tenantId) {
    entityManager
        .createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended(cast(:t as text), 42))")
        .setParameter("t", tenantId.toString())
        .getResultList();
  }

  private static AuditEntry toEntry(AuditLog row) {
    return new AuditEntry(
        row.getTenantId(),
        row.getRequestId(),
        row.getActor(),
        row.getModel(),
        Verdict.from(row.getVerdict()),
        row.getPromptRedacted(),
        row.getResponseRedacted(),
        row.getCreatedAt().toInstant());
  }
}

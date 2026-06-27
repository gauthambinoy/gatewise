package com.auvex.gateway.audit;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Appends entries to a tenant's hash chain and verifies it.
 *
 * <p>Each append takes a per-tenant {@link ChainLock} for the duration of its transaction, so two
 * concurrent appends for the same tenant can't both read the same chain head and fork the chain;
 * different tenants never contend. The lock is backend-specific (a Postgres advisory lock, or a
 * no-op on the single-writer SQLite backend), so this code is the same on either. The write is
 * synchronous (an async pipeline is a later step) but is just a couple of indexed statements.
 */
@Component
public class AuditService {

  private final AuditLogRepository repository;
  private final ChainLock chainLock;
  private final ApplicationEventPublisher events;

  public AuditService(
      AuditLogRepository repository, ChainLock chainLock, ApplicationEventPublisher events) {
    this.repository = repository;
    this.chainLock = chainLock;
    this.events = events;
  }

  /** Appends an entry to its tenant's chain, computing and storing the link hashes. */
  @Transactional
  public AuditLog append(AuditEntry entry) {
    chainLock.lockForAppend(entry.tenantId());
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
            createdAt,
            entry.promptTokens(),
            entry.completionTokens(),
            entry.costUsd(),
            entry.redactionCounts(),
            entry.principalType(),
            entry.principalId(),
            entry.principalEmail());
    AuditLog saved = repository.save(row);
    events.publishEvent(new AuditRecordedEvent(saved));
    return saved;
  }

  /**
   * Walks a tenant's chain oldest-first and returns the id of the first broken link, or empty if
   * the chain is intact. A broken link is a prev_hash that doesn't continue the chain, or an entry
   * whose stored hash doesn't match a fresh recomputation (i.e. a tampered field).
   */
  @Transactional(readOnly = true)
  public Optional<Long> firstBrokenLink(UUID tenantId) {
    // The earliest remaining entry anchors the chain: data-retention may legitimately have purged
    // an
    // older prefix, so we accept the first row's prev_hash as-is and check contiguity from there.
    // Tampering (the entry-hash recomputation folds in prev_hash) and mid-chain deletion (a
    // prev_hash
    // that doesn't continue) are still detected.
    String expectedPrev = null;
    for (AuditLog row : repository.findByTenantIdOrderByIdAsc(tenantId)) {
      if (expectedPrev != null && !expectedPrev.equals(row.getPrevHash())) {
        return Optional.of(row.getId());
      }
      if (!AuditChain.entryHash(row.getPrevHash(), toEntry(row)).equals(row.getEntryHash())) {
        return Optional.of(row.getId());
      }
      expectedPrev = row.getEntryHash();
    }
    return Optional.empty();
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
        row.getCreatedAt().toInstant(),
        row.getPromptTokens(),
        row.getCompletionTokens(),
        row.getCostUsd(),
        row.getRedactionCounts());
  }
}

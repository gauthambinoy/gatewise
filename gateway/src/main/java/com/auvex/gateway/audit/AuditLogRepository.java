package com.auvex.gateway.audit;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Data access for audit-log rows. */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

  /** Total calls for a tenant (for the usage summary). */
  long countByTenantId(UUID tenantId);

  /**
   * Deletes entries older than the cutoff that are NOT under legal hold (data-retention
   * enforcement); returns how many. Held entries are preserved regardless of age.
   */
  @org.springframework.transaction.annotation.Transactional
  @org.springframework.data.jpa.repository.Modifying
  long deleteByCreatedAtBeforeAndLegalHoldFalse(java.time.OffsetDateTime cutoff);

  /** Places or releases a legal hold on every entry for one data subject; returns rows affected. */
  @org.springframework.transaction.annotation.Transactional
  @org.springframework.data.jpa.repository.Modifying
  @Query(
      "UPDATE AuditLog a SET a.legalHold = :hold"
          + " WHERE a.tenantId = :tenantId AND a.actor = :actor")
  int setLegalHoldForActor(
      @Param("tenantId") UUID tenantId, @Param("actor") String actor, @Param("hold") boolean hold);

  /** Calls for a tenant with a given verdict. */
  long countByTenantIdAndVerdict(UUID tenantId, String verdict);

  /** Forwarded calls for a tenant in a window — the budget basis. */
  long countByTenantIdAndVerdictInAndCreatedAtAfter(
      UUID tenantId, java.util.Collection<String> verdicts, java.time.OffsetDateTime after);

  /** Per-model call counts for a tenant. */
  @Query(
      "SELECT a.model AS model, COUNT(a) AS count FROM AuditLog a"
          + " WHERE a.tenantId = :tenantId GROUP BY a.model")
  List<ModelCount> countByModel(@Param("tenantId") UUID tenantId);

  /** Projection for the per-model aggregation. */
  interface ModelCount {
    String getModel();

    long getCount();
  }

  /** Total USD cost recorded for a tenant (0 when nothing has cost data). */
  @Query("SELECT COALESCE(SUM(a.costUsd), 0) FROM AuditLog a WHERE a.tenantId = :tenantId")
  BigDecimal sumCostByTenantId(@Param("tenantId") UUID tenantId);

  /** Total USD cost for a tenant since a cutoff — the basis for a spend window and projection. */
  @Query(
      "SELECT COALESCE(SUM(a.costUsd), 0) FROM AuditLog a"
          + " WHERE a.tenantId = :tenantId AND a.createdAt >= :after")
  BigDecimal sumCostByTenantIdAndCreatedAtAfter(
      @Param("tenantId") UUID tenantId, @Param("after") java.time.OffsetDateTime after);

  /** Total USD cost per model for a tenant (chargeback by model). */
  @Query(
      "SELECT a.model AS model, COALESCE(SUM(a.costUsd), 0) AS total FROM AuditLog a"
          + " WHERE a.tenantId = :tenantId GROUP BY a.model")
  List<ModelCost> costByModel(@Param("tenantId") UUID tenantId);

  /** Projection for the per-model cost aggregation. */
  interface ModelCost {
    String getModel();

    BigDecimal getTotal();
  }

  /** Total tokens (prompt + completion) recorded for a tenant. */
  @Query(
      "SELECT COALESCE(SUM(a.promptTokens), 0) + COALESCE(SUM(a.completionTokens), 0)"
          + " FROM AuditLog a WHERE a.tenantId = :tenantId")
  long sumTokensByTenantId(@Param("tenantId") UUID tenantId);

  /** Total redactions per data type for a tenant, summed across the jsonb tallies. */
  @Query(
      value =
          "SELECT kv.key AS type, SUM(kv.value::int) AS total"
              + " FROM audit_log a, jsonb_each_text(a.redaction_counts) AS kv"
              + " WHERE a.tenant_id = :tenantId AND a.redaction_counts IS NOT NULL"
              + " GROUP BY kv.key",
      nativeQuery = true)
  List<TypeCount> sumRedactionByType(@Param("tenantId") UUID tenantId);

  /** Projection for the per-type redaction aggregation. */
  interface TypeCount {
    String getType();

    long getTotal();
  }

  /** Per-actor usage for a tenant: requests, redacted, blocked counts and cost. */
  @Query(
      "SELECT a.actor AS actor, COUNT(a) AS requests,"
          + " SUM(CASE WHEN a.verdict = 'redacted' THEN 1 ELSE 0 END) AS redacted,"
          + " SUM(CASE WHEN a.verdict = 'blocked' THEN 1 ELSE 0 END) AS blocked,"
          + " COALESCE(SUM(a.costUsd), 0) AS cost"
          + " FROM AuditLog a WHERE a.tenantId = :tenantId GROUP BY a.actor")
  List<UserUsage> usageByActor(@Param("tenantId") UUID tenantId);

  /** Projection for the per-actor usage aggregation. */
  interface UserUsage {
    String getActor();

    long getRequests();

    long getRedacted();

    long getBlocked();

    BigDecimal getCost();
  }

  /** The current head of a tenant's chain (its most recent entry), if any. */
  Optional<AuditLog> findTopByTenantIdOrderByIdDesc(UUID tenantId);

  /** A tenant's chain oldest-first, for verification. */
  List<AuditLog> findByTenantIdOrderByIdAsc(UUID tenantId);

  /** Every entry recorded for one actor (data subject), for a GDPR access request. */
  List<AuditLog> findByTenantIdAndActorOrderByIdAsc(UUID tenantId, String actor);

  /** One entry, only if it belongs to the tenant (for the request-detail view). */
  Optional<AuditLog> findByIdAndTenantId(Long id, UUID tenantId);

  /** A page of a tenant's entries, for the query API. */
  Page<AuditLog> findByTenantId(UUID tenantId, Pageable pageable);

  /** A page of a tenant's entries with a given verdict. */
  Page<AuditLog> findByTenantIdAndVerdict(UUID tenantId, String verdict, Pageable pageable);

  /**
   * Free-text search over the redacted prompt, model and actor (case-insensitive), with an optional
   * verdict filter. Tenant-scoped; the verdict is applied only when non-null.
   *
   * <p>Caveat: when field-level encryption is on (auvex.encryption.enabled), the prompt column
   * holds ciphertext, so this LIKE can't match against it — free-text matches on the prompt won't
   * be found while encryption is enabled (model and actor, which aren't encrypted, still match).
   * This is an inherent trade-off of encrypting at rest, not a bug; searchable encryption would
   * need a separate index and is out of scope here.
   */
  @Query(
      "SELECT a FROM AuditLog a WHERE a.tenantId = :tenantId"
          + " AND (:verdict IS NULL OR a.verdict = :verdict)"
          + " AND (LOWER(a.promptRedacted) LIKE LOWER(CONCAT('%', :q, '%'))"
          + " OR LOWER(a.model) LIKE LOWER(CONCAT('%', :q, '%'))"
          + " OR LOWER(a.actor) LIKE LOWER(CONCAT('%', :q, '%')))")
  Page<AuditLog> search(
      @Param("tenantId") UUID tenantId,
      @Param("verdict") String verdict,
      @Param("q") String q,
      Pageable pageable);
}

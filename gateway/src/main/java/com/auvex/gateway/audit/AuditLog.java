package com.auvex.gateway.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One persisted, hash-chained audit record. Maps the {@code audit_log} table. */
@Entity
@Table(name = "audit_log")
public class AuditLog {

  // Monotonic per the identity sequence; the chain head for a tenant is its highest id.
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "request_id", nullable = false)
  private UUID requestId;

  @Column private String actor;

  @Column private String model;

  @Column(nullable = false)
  private String verdict;

  @Column(name = "prompt_redacted")
  private String promptRedacted;

  @Column(name = "response_redacted")
  private String responseRedacted;

  @Column(name = "prev_hash")
  private String prevHash;

  @Column(name = "entry_hash", nullable = false)
  private String entryHash;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "prompt_tokens")
  private Integer promptTokens;

  @Column(name = "completion_tokens")
  private Integer completionTokens;

  @Column(name = "cost_usd")
  private BigDecimal costUsd;

  // Per-type masking tallies (e.g. {"email":2}); analytics metadata, not part of the hash chain.
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "redaction_counts", columnDefinition = "jsonb")
  private Map<String, Integer> redactionCounts;

  // Who made the call: "api_key" or "member" (attribution metadata, not part of the hash chain).
  @Column(name = "principal_type")
  private String principalType;

  @Column(name = "principal_id")
  private UUID principalId;

  @Column(name = "principal_email")
  private String principalEmail;

  /** JPA requires a no-arg constructor; not for application use. */
  protected AuditLog() {}

  /** Creates a row with its already-computed chain hashes. */
  public AuditLog(
      UUID tenantId,
      UUID requestId,
      String actor,
      String model,
      String verdict,
      String promptRedacted,
      String responseRedacted,
      String prevHash,
      String entryHash,
      OffsetDateTime createdAt,
      Integer promptTokens,
      Integer completionTokens,
      BigDecimal costUsd,
      Map<String, Integer> redactionCounts,
      String principalType,
      UUID principalId,
      String principalEmail) {
    this.tenantId = tenantId;
    this.requestId = requestId;
    this.actor = actor;
    this.model = model;
    this.verdict = verdict;
    this.promptRedacted = promptRedacted;
    this.responseRedacted = responseRedacted;
    this.prevHash = prevHash;
    this.entryHash = entryHash;
    this.createdAt = createdAt;
    this.promptTokens = promptTokens;
    this.completionTokens = completionTokens;
    this.costUsd = costUsd;
    this.redactionCounts = redactionCounts;
    this.principalType = principalType;
    this.principalId = principalId;
    this.principalEmail = principalEmail;
  }

  public Long getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public UUID getRequestId() {
    return requestId;
  }

  public String getActor() {
    return actor;
  }

  public String getModel() {
    return model;
  }

  public String getVerdict() {
    return verdict;
  }

  public String getPromptRedacted() {
    return promptRedacted;
  }

  public String getResponseRedacted() {
    return responseRedacted;
  }

  public String getPrevHash() {
    return prevHash;
  }

  public String getEntryHash() {
    return entryHash;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public Integer getPromptTokens() {
    return promptTokens;
  }

  public Integer getCompletionTokens() {
    return completionTokens;
  }

  public BigDecimal getCostUsd() {
    return costUsd;
  }

  public Map<String, Integer> getRedactionCounts() {
    return redactionCounts;
  }

  public String getPrincipalType() {
    return principalType;
  }

  public UUID getPrincipalId() {
    return principalId;
  }

  public String getPrincipalEmail() {
    return principalEmail;
  }
}

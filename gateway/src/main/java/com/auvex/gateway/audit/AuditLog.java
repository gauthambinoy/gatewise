package com.auvex.gateway.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

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
      OffsetDateTime createdAt) {
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
}

package com.auvex.gateway.approval;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** A call held for human approval. Maps the {@code held_request} table. */
@Entity
@Table(name = "held_request")
public class HeldRequest {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(nullable = false)
  private String actor;

  @Column(nullable = false)
  private String model;

  @Column(name = "prompt_hash", nullable = false)
  private String promptHash;

  @Column(name = "prompt_redacted")
  private String promptRedacted;

  @Column private String reason;

  @Column(nullable = false)
  private String status = "pending"; // 'pending' | 'approved' | 'rejected'

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "decided_at")
  private OffsetDateTime decidedAt;

  @Column(name = "decided_by")
  private String decidedBy;

  /** JPA requires a no-arg constructor; not for application use. */
  protected HeldRequest() {}

  /** Creates a new pending hold for a request that matched a review trigger. */
  public HeldRequest(
      UUID tenantId,
      String actor,
      String model,
      String promptHash,
      String promptRedacted,
      String reason) {
    this.tenantId = tenantId;
    this.actor = actor;
    this.model = model;
    this.promptHash = promptHash;
    this.promptRedacted = promptRedacted;
    this.reason = reason;
    this.status = "pending";
  }

  /** Records a reviewer's decision ({@code approved} or {@code rejected}). */
  public void decide(String decision, String decidedBy, OffsetDateTime when) {
    this.status = decision;
    this.decidedBy = decidedBy;
    this.decidedAt = when;
  }

  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public String getActor() {
    return actor;
  }

  public String getModel() {
    return model;
  }

  public String getPromptHash() {
    return promptHash;
  }

  public String getPromptRedacted() {
    return promptRedacted;
  }

  public String getReason() {
    return reason;
  }

  public String getStatus() {
    return status;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getDecidedAt() {
    return decidedAt;
  }

  public String getDecidedBy() {
    return decidedBy;
  }
}

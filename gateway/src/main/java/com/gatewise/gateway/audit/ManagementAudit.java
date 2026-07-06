package com.gatewise.gateway.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * One record of a console management action — a key, member or policy change — and the principal
 * who performed it. Unlike {@link AuditLog} (the hash-chained record of governed AI calls), this is
 * a plain attribution log for administrative changes, so it isn't part of the tamper-evident chain.
 */
@Entity
@Table(name = "management_audit")
public class ManagementAudit {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "principal_type")
  private String principalType;

  @Column(name = "principal_id")
  private UUID principalId;

  @Column(name = "principal_email")
  private String principalEmail;

  @Column(nullable = false)
  private String action;

  @Column(name = "resource_type", nullable = false)
  private String resourceType;

  @Column(name = "resource_id")
  private UUID resourceId;

  @Column private String detail;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  /** JPA requires a no-arg constructor; not for application use. */
  protected ManagementAudit() {}

  public ManagementAudit(
      UUID tenantId,
      String principalType,
      UUID principalId,
      String principalEmail,
      String action,
      String resourceType,
      UUID resourceId,
      String detail) {
    this.tenantId = tenantId;
    this.principalType = principalType;
    this.principalId = principalId;
    this.principalEmail = principalEmail;
    this.action = action;
    this.resourceType = resourceType;
    this.resourceId = resourceId;
    this.detail = detail;
  }

  public Long getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
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

  public String getAction() {
    return action;
  }

  public String getResourceType() {
    return resourceType;
  }

  public UUID getResourceId() {
    return resourceId;
  }

  public String getDetail() {
    return detail;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}

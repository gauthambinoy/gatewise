package com.auvex.gateway.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** A human who can sign in to a tenant's console, with a role. Maps the {@code member} table. */
@Entity
@Table(name = "member")
public class Member {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(nullable = false)
  private String email;

  @Column private String name;

  @Column(nullable = false)
  private String role; // 'owner' | 'security_admin' | 'auditor'

  @Column(nullable = false)
  private String status = "invited"; // 'invited' | 'active'

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  /** JPA requires a no-arg constructor; not for application use. */
  protected Member() {}

  /** Invites a member to a tenant with a role and status. */
  public Member(UUID tenantId, String email, String name, String role, String status) {
    this.tenantId = tenantId;
    this.email = email;
    this.name = name;
    this.role = role;
    this.status = status;
  }

  /** Replaces this member's mutable fields (used by the update endpoint). */
  public void update(String name, String role, String status) {
    this.name = name;
    this.role = role;
    this.status = status;
  }

  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public String getEmail() {
    return email;
  }

  public String getName() {
    return name;
  }

  public String getRole() {
    return role;
  }

  public String getStatus() {
    return status;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}

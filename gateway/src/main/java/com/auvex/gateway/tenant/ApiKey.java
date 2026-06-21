package com.auvex.gateway.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * A credential a caller presents, which resolves to exactly one tenant.
 *
 * <p>Only the SHA-256 hash of the raw key is stored ({@code key_hash}); the raw secret is never
 * persisted. A short {@code prefix} is kept purely so a human can recognise a key in a list.
 */
@Entity
@Table(name = "api_key")
public class ApiKey {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(nullable = false)
  private String name;

  @Column(name = "key_hash", nullable = false, unique = true)
  private String keyHash;

  @Column(nullable = false)
  private String prefix;

  // 'active' or 'revoked'.
  @Column(nullable = false)
  private String status = "active";

  @Column(name = "last_used_at")
  private OffsetDateTime lastUsedAt;

  @Column(name = "expires_at")
  private OffsetDateTime expiresAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  /** JPA requires a no-arg constructor; not for application use. */
  protected ApiKey() {}

  /** Creates a new active key for a tenant from an already-hashed secret. */
  public ApiKey(UUID tenantId, String name, String keyHash, String prefix) {
    this.tenantId = tenantId;
    this.name = name;
    this.keyHash = keyHash;
    this.prefix = prefix;
  }

  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public String getName() {
    return name;
  }

  public String getKeyHash() {
    return keyHash;
  }

  public String getPrefix() {
    return prefix;
  }

  public String getStatus() {
    return status;
  }

  public OffsetDateTime getLastUsedAt() {
    return lastUsedAt;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getExpiresAt() {
    return expiresAt;
  }

  /** True while the key can still authenticate (i.e. not revoked). */
  public boolean isActive() {
    return "active".equals(status);
  }

  /** True once an optional expiry instant has passed. */
  public boolean isExpired() {
    return expiresAt != null && expiresAt.isBefore(OffsetDateTime.now());
  }

  /** Revokes the key so it can no longer authenticate. */
  public void revoke() {
    this.status = "revoked";
  }

  /** Sets an expiry instant (used to model short-lived keys). */
  public void setExpiresAt(OffsetDateTime expiresAt) {
    this.expiresAt = expiresAt;
  }
}

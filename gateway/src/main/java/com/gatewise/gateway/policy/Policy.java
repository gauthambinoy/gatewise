package com.gatewise.gateway.policy;

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

/** A stored allow/deny rule for one tenant. Maps the {@code policy} table. */
@Entity
@Table(name = "policy")
public class Policy {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String effect; // 'allow' | 'deny'

  @Column(name = "resource_type", nullable = false)
  private String resourceType; // 'model' | 'data_type' | 'user'

  @Column(name = "resource_value", nullable = false)
  private String resourceValue;

  @Column(nullable = false)
  private int priority;

  @Column(nullable = false)
  private boolean enabled;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  /** JPA requires a no-arg constructor; not for application use. */
  protected Policy() {}

  /** Creates a rule for a tenant. */
  public Policy(
      UUID tenantId,
      String name,
      String effect,
      String resourceType,
      String resourceValue,
      int priority,
      boolean enabled) {
    this.tenantId = tenantId;
    this.name = name;
    this.effect = effect;
    this.resourceType = resourceType;
    this.resourceValue = resourceValue;
    this.priority = priority;
    this.enabled = enabled;
  }

  /** Replaces this rule's mutable fields (used by the update endpoint). */
  public void update(
      String name,
      String effect,
      String resourceType,
      String resourceValue,
      int priority,
      boolean enabled) {
    this.name = name;
    this.effect = effect;
    this.resourceType = resourceType;
    this.resourceValue = resourceValue;
    this.priority = priority;
    this.enabled = enabled;
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

  public String getEffect() {
    return effect;
  }

  public String getResourceType() {
    return resourceType;
  }

  public String getResourceValue() {
    return resourceValue;
  }

  public int getPriority() {
    return priority;
  }

  public boolean isEnabled() {
    return enabled;
  }
}

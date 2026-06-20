package com.auvex.gateway.tenant;

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

/** A customer organisation — the root of multi-tenant isolation. Maps the {@code tenant} table. */
@Entity
@Table(name = "tenant")
public class Tenant {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, unique = true)
  private String slug;

  // 'active' or 'suspended'. A suspended tenant's keys still resolve but are refused at the door.
  @Column(nullable = false)
  private String status = "active";

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  /** JPA requires a no-arg constructor; not for application use. */
  protected Tenant() {}

  /** Creates a new active tenant with the given display name and url-safe slug. */
  public Tenant(String name, String slug) {
    this.name = name;
    this.slug = slug;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getSlug() {
    return slug;
  }

  public String getStatus() {
    return status;
  }

  /** True when the tenant is allowed to use the gateway at all. */
  public boolean isActive() {
    return "active".equals(status);
  }
}

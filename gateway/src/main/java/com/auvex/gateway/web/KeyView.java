package com.auvex.gateway.web;

import com.auvex.gateway.tenant.ApiKey;
import java.time.OffsetDateTime;
import java.util.UUID;

/** The public, non-secret representation of an API key (the raw secret is never shown again). */
public record KeyView(
    UUID id,
    String name,
    String prefix,
    String status,
    OffsetDateTime createdAt,
    OffsetDateTime lastUsedAt) {

  public static KeyView of(ApiKey key) {
    return new KeyView(
        key.getId(),
        key.getName(),
        key.getPrefix(),
        key.getStatus(),
        key.getCreatedAt(),
        key.getLastUsedAt());
  }
}

package com.gatewise.gateway.web;

import com.gatewise.gateway.audit.ManagementAudit;
import java.time.OffsetDateTime;
import java.util.UUID;

/** The public representation of one console management-action record. */
public record ManagementAuditView(
    Long id,
    String principalType,
    UUID principalId,
    String principalEmail,
    String action,
    String resourceType,
    UUID resourceId,
    String detail,
    OffsetDateTime createdAt) {

  /** Projects a stored management-audit row into its API view. */
  public static ManagementAuditView of(ManagementAudit row) {
    return new ManagementAuditView(
        row.getId(),
        row.getPrincipalType(),
        row.getPrincipalId(),
        row.getPrincipalEmail(),
        row.getAction(),
        row.getResourceType(),
        row.getResourceId(),
        row.getDetail(),
        row.getCreatedAt());
  }
}

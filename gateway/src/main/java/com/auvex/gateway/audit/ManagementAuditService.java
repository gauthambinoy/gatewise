package com.auvex.gateway.audit;

import com.auvex.gateway.auth.AuthenticatedTenant;
import com.auvex.gateway.auth.TenantContext;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Records console management actions against the acting principal (the API key or console member
 * bound to the request). Fail-open: a problem writing this attribution log must never break the
 * administrative operation it describes, so a failure is logged and swallowed.
 */
@Component
public class ManagementAuditService {

  private static final Logger LOG = LoggerFactory.getLogger(ManagementAuditService.class);

  private final ManagementAuditRepository repository;

  public ManagementAuditService(ManagementAuditRepository repository) {
    this.repository = repository;
  }

  /** Records that the current principal performed {@code action} on a resource. */
  public void record(String action, String resourceType, UUID resourceId, String detail) {
    try {
      AuthenticatedTenant principal = TenantContext.require();
      repository.save(
          new ManagementAudit(
              principal.tenantId(),
              principal.principalType(),
              principal.principalId(),
              principal.memberEmail(),
              action,
              resourceType,
              resourceId,
              detail));
    } catch (RuntimeException e) {
      // Never let an attribution-log failure break the administrative action it records.
      LOG.warn("Failed to record management action '{}' on {}", action, resourceType, e);
    }
  }
}

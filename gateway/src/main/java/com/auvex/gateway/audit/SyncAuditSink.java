package com.auvex.gateway.audit;

import com.auvex.gateway.auth.TenantContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Default sink: persist the entry synchronously on the request thread. */
@Component
@ConditionalOnProperty(name = "auvex.audit.async", havingValue = "false", matchIfMissing = true)
public class SyncAuditSink implements AuditSink {

  private final AuditService audit;

  public SyncAuditSink(AuditService audit) {
    this.audit = audit;
  }

  @Override
  public void record(AuditEntry entry) {
    // Stamp the acting principal here, on the request thread, where it is still bound.
    audit.append(entry.withPrincipal(TenantContext.get()));
  }
}

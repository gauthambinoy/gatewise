package com.auvex.gateway.budget;

import com.auvex.gateway.audit.AuditLogRepository;
import com.auvex.gateway.config.BudgetProperties;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Decides whether a tenant still has call budget left.
 *
 * <p>The budget is counted from the audit log: only forwarded calls (allowed or redacted) in the
 * rolling window count, so blocked or budget-rejected attempts don't consume it. When budgets are
 * disabled, every call is allowed.
 */
@Component
public class BudgetService {

  // Only successful forwards count against a budget.
  private static final List<String> COUNTED = List.of("allowed", "redacted");

  private final AuditLogRepository audit;
  private final BudgetProperties properties;

  public BudgetService(AuditLogRepository audit, BudgetProperties properties) {
    this.audit = audit;
    this.properties = properties;
  }

  /** True if the tenant can make one more call within its budget. */
  public boolean allows(UUID tenantId) {
    if (!properties.enabled()) {
      return true;
    }
    OffsetDateTime windowStart = OffsetDateTime.now().minus(properties.window());
    long used = audit.countByTenantIdAndVerdictInAndCreatedAtAfter(tenantId, COUNTED, windowStart);
    return used < properties.maxCalls();
  }
}

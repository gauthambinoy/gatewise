package com.auvex.gateway.web;

import com.auvex.gateway.audit.AuditLogRepository;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Computes a tenant's usage summary from its audit entries. */
@Component
public class UsageService {

  private final AuditLogRepository audit;

  public UsageService(AuditLogRepository audit) {
    this.audit = audit;
  }

  /** Aggregates the tenant's calls by verdict and by model. */
  public UsageSummary summarize(UUID tenantId) {
    Map<String, Long> byModel =
        audit.countByModel(tenantId).stream()
            .collect(
                Collectors.toMap(
                    count -> count.getModel() == null ? "unknown" : count.getModel(),
                    AuditLogRepository.ModelCount::getCount));
    Map<String, Long> redactionByType =
        audit.sumRedactionByType(tenantId).stream()
            .collect(
                Collectors.toMap(
                    AuditLogRepository.TypeCount::getType, AuditLogRepository.TypeCount::getTotal));
    return new UsageSummary(
        audit.countByTenantId(tenantId),
        audit.countByTenantIdAndVerdict(tenantId, "allowed"),
        audit.countByTenantIdAndVerdict(tenantId, "blocked"),
        audit.countByTenantIdAndVerdict(tenantId, "redacted"),
        audit.countByTenantIdAndVerdict(tenantId, "error"),
        byModel,
        audit.sumCostByTenantId(tenantId),
        audit.sumTokensByTenantId(tenantId),
        redactionByType);
  }
}

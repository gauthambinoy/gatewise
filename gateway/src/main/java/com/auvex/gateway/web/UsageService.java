package com.auvex.gateway.web;

import com.auvex.gateway.audit.AuditLogRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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

  /** Per-user usage for the tenant, aggregated from the audit actor. */
  public List<UserUsageView> byUser(UUID tenantId) {
    return audit.usageByActor(tenantId).stream()
        .map(
            u ->
                new UserUsageView(
                    u.getActor(), u.getRequests(), u.getRedacted(), u.getBlocked(), u.getCost()))
        .toList();
  }

  /** A cost chargeback/showback report: spend by model and user, plus a monthly projection. */
  public ChargebackReport chargeback(UUID tenantId) {
    Map<String, BigDecimal> costByModel =
        audit.costByModel(tenantId).stream()
            .collect(
                Collectors.toMap(
                    c -> c.getModel() == null ? "unknown" : c.getModel(),
                    AuditLogRepository.ModelCost::getTotal));
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    BigDecimal last30 = audit.sumCostByTenantIdAndCreatedAtAfter(tenantId, now.minusDays(30));
    BigDecimal last7 = audit.sumCostByTenantIdAndCreatedAtAfter(tenantId, now.minusDays(7));
    // Project the trailing week's spend out to 30 days — a rough monthly run-rate.
    BigDecimal projected =
        last7
            .multiply(BigDecimal.valueOf(30))
            .divide(BigDecimal.valueOf(7), 6, RoundingMode.HALF_UP);
    return new ChargebackReport(
        audit.sumCostByTenantId(tenantId), costByModel, byUser(tenantId), last30, projected);
  }
}

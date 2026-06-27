package com.auvex.gateway.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * A cost-focused chargeback/showback view for a tenant: what was spent, broken down by model and by
 * user, plus a simple forward projection so a team can see where the budget is going.
 *
 * @param totalCostUsd all-time recorded spend
 * @param costByModel spend per provider model (chargeback by model)
 * @param costByUser spend per user/service actor (chargeback by team member)
 * @param last30DaysCostUsd recent actual spend
 * @param projectedMonthlyCostUsd a 30-day projection from the last 7 days of spend (showback)
 */
public record ChargebackReport(
    BigDecimal totalCostUsd,
    Map<String, BigDecimal> costByModel,
    List<UserUsageView> costByUser,
    BigDecimal last30DaysCostUsd,
    BigDecimal projectedMonthlyCostUsd) {}

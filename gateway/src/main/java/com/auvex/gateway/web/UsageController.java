package com.auvex.gateway.web;

import com.auvex.gateway.auth.TenantContext;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Returns a usage summary for the calling tenant. */
@RestController
@RequestMapping("/v1/usage")
public class UsageController {

  private final UsageService usage;

  public UsageController(UsageService usage) {
    this.usage = usage;
  }

  /** The tenant's call counts by verdict and by model. */
  @GetMapping
  public UsageSummary summary() {
    return usage.summarize(TenantContext.require().tenantId());
  }

  /** The tenant's usage broken down per user (actor). */
  @GetMapping("/users")
  public List<UserUsageView> byUser() {
    return usage.byUser(TenantContext.require().tenantId());
  }

  /** A cost chargeback/showback report: spend by model and user, plus a monthly projection. */
  @GetMapping("/chargeback")
  public ChargebackReport chargeback() {
    return usage.chargeback(TenantContext.require().tenantId());
  }
}

package com.auvex.gateway.web;

import com.auvex.gateway.auth.TenantContext;
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
}

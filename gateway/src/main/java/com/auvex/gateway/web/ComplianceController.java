package com.auvex.gateway.web;

import com.auvex.gateway.audit.AuditLogRepository;
import com.auvex.gateway.audit.AuditLogRepository.TypeCount;
import com.auvex.gateway.audit.AuditService;
import com.auvex.gateway.auth.TenantContext;
import com.auvex.gateway.config.ComplianceProperties;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A point-in-time compliance report for the tenant, derived from the audit log and the gateway's
 * active controls. It maps the controls the gateway actually enforces — data minimisation, an
 * immutable audit trail, access control, content screening, retention — to the frameworks they
 * support (GDPR, the EU AI Act, DORA), with live evidence so the report is real, not a checklist.
 */
@RestController
@RequestMapping("/v1")
public class ComplianceController {

  private final AuditLogRepository audit;
  private final AuditService auditService;
  private final ComplianceProperties properties;

  public ComplianceController(
      AuditLogRepository audit, AuditService auditService, ComplianceProperties properties) {
    this.audit = audit;
    this.auditService = auditService;
    this.properties = properties;
  }

  /** Builds the compliance report for the calling tenant. */
  @GetMapping("/compliance/report")
  public ComplianceReport report() {
    UUID tenantId = TenantContext.require().tenantId();

    long total = audit.countByTenantId(tenantId);
    Map<String, Long> byVerdict = new LinkedHashMap<>();
    for (String verdict : List.of("allowed", "redacted", "blocked", "error")) {
      byVerdict.put(verdict, audit.countByTenantIdAndVerdict(tenantId, verdict));
    }

    Map<String, Long> piiByType = new LinkedHashMap<>();
    long piiTotal = 0;
    for (TypeCount row : audit.sumRedactionByType(tenantId)) {
      piiByType.put(row.getType(), row.getTotal());
      piiTotal += row.getTotal();
    }

    Optional<Long> firstBroken = auditService.firstBrokenLink(tenantId);
    boolean intact = firstBroken.isEmpty();
    int retention = properties.retentionDays();

    List<ControlStatus> controls =
        List.of(
            new ControlStatus(
                "PII & secret minimisation",
                "GDPR Art.5 · EU AI Act",
                "active",
                piiTotal + " sensitive items masked before leaving the network"),
            new ControlStatus(
                "Immutable audit trail",
                "EU AI Act Art.12 · DORA",
                intact ? "verified" : "COMPROMISED",
                intact ? "hash chain verified intact" : "chain broken at id " + firstBroken.get()),
            new ControlStatus(
                "Access control & tenant isolation",
                "GDPR Art.32 · ISO 27001",
                "active",
                "per-tenant hashed API-key authentication"),
            new ControlStatus(
                "Prompt-injection & content-safety screening",
                "EU AI Act risk controls",
                "active",
                "every prompt screened before forwarding"),
            new ControlStatus(
                "Data retention",
                "GDPR Art.5(1)(e)",
                "active",
                retention + "-day retention window"),
            new ControlStatus(
                "Right of access (DSAR)",
                "GDPR Art.15",
                "available",
                "subject export at /v1/audit/dsar"));

    return new ComplianceReport(
        Instant.now(),
        total,
        byVerdict,
        piiTotal,
        piiByType,
        intact,
        firstBroken.orElse(null),
        retention,
        controls);
  }

  /** The compliance report. */
  public record ComplianceReport(
      Instant generatedAt,
      long totalCalls,
      Map<String, Long> callsByVerdict,
      long piiItemsMasked,
      Map<String, Long> piiMaskedByType,
      boolean auditChainIntact,
      Long firstBrokenAuditId,
      int retentionDays,
      List<ControlStatus> controls) {}

  /** One enforced control mapped to the framework it supports, with live evidence. */
  public record ControlStatus(
      String control, String framework, String status, String evidence) {}
}

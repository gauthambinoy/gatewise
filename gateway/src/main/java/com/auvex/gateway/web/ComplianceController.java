package com.auvex.gateway.web;

import com.auvex.gateway.audit.AuditChain;
import com.auvex.gateway.audit.AuditLog;
import com.auvex.gateway.audit.AuditLogRepository;
import com.auvex.gateway.audit.AuditLogRepository.TypeCount;
import com.auvex.gateway.audit.AuditService;
import com.auvex.gateway.audit.NotarizationAnchor;
import com.auvex.gateway.audit.NotaryPublisher;
import com.auvex.gateway.auth.TenantContext;
import com.auvex.gateway.config.ComplianceProperties;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
  private final NotaryPublisher notary;

  public ComplianceController(
      AuditLogRepository audit,
      AuditService auditService,
      ComplianceProperties properties,
      NotaryPublisher notary) {
    this.audit = audit;
    this.auditService = auditService;
    this.properties = properties;
    this.notary = notary;
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

  /**
   * Returns the tenant's current chain head as a notarization anchor: the head entry's id and hash,
   * whether the chain verifies intact right now, and when it was produced. An operator publishes
   * this to an external timestamping/notary service to get an independent, dated record that the
   * audit log was in this state at this time. If {@code auvex.compliance.notary-url} is set, the
   * gateway also POSTs the anchor there (fail-open). Tenant-scoped.
   */
  @GetMapping("/compliance/notarization")
  public NotarizationAnchor notarization() {
    UUID tenantId = TenantContext.require().tenantId();
    Optional<AuditLog> head = audit.findTopByTenantIdOrderByIdDesc(tenantId);
    boolean intact = auditService.firstBrokenLink(tenantId).isEmpty();
    NotarizationAnchor anchor =
        new NotarizationAnchor(
            tenantId,
            head.map(AuditLog::getId).orElse(null),
            head.map(AuditLog::getEntryHash).orElse(AuditChain.GENESIS),
            Instant.now(),
            intact);
    notary.publish(anchor);
    return anchor;
  }

  /**
   * Places or releases a legal hold on every audit entry for a data subject (matched on the actor).
   * Held entries are exempt from retention deletion — the control for preserving a subject's
   * records during litigation or an investigation, satisfying a legal-hold obligation over the GDPR
   * retention default.
   */
  @PostMapping("/compliance/legal-hold")
  public LegalHoldResult legalHold(@RequestBody(required = false) LegalHoldRequest request) {
    if (request == null || request.subject() == null || request.subject().isBlank()) {
      throw new InvalidRequestException("'subject' is required.");
    }
    UUID tenantId = TenantContext.require().tenantId();
    int affected = audit.setLegalHoldForActor(tenantId, request.subject(), request.hold());
    return new LegalHoldResult(request.subject(), request.hold(), affected);
  }

  /** A request to place ({@code hold=true}) or release a legal hold on a data subject's entries. */
  public record LegalHoldRequest(String subject, boolean hold) {}

  /** The outcome of a legal-hold change: the subject, the new state, and rows affected. */
  public record LegalHoldResult(String subject, boolean hold, int affected) {}

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
  public record ControlStatus(String control, String framework, String status, String evidence) {}
}

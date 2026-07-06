package com.gatewise.gateway.web;

import com.gatewise.gateway.audit.AuditLog;
import com.gatewise.gateway.audit.AuditLogRepository;
import com.gatewise.gateway.audit.AuditService;
import com.gatewise.gateway.audit.ManagementAudit;
import com.gatewise.gateway.audit.ManagementAuditRepository;
import com.gatewise.gateway.auth.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read the audit trail for the calling tenant.
 *
 * <p>Both endpoints are scoped to the tenant resolved from the API key, so a tenant only ever sees
 * its own entries. {@code /verify} re-walks the chain and reports whether it's intact.
 */
@RestController
@RequestMapping("/v1/audit")
public class AuditController {

  private final AuditLogRepository entries;
  private final ManagementAuditRepository managementEntries;
  private final AuditService audit;

  public AuditController(
      AuditLogRepository entries, ManagementAuditRepository managementEntries, AuditService audit) {
    this.entries = entries;
    this.managementEntries = managementEntries;
    this.audit = audit;
  }

  /**
   * A page of the tenant's audit entries, newest first. Optionally filtered by {@code verdict}, and
   * narrowed by a free-text {@code q} over the redacted prompt, model and actor.
   */
  @GetMapping
  public AuditPage query(
      @RequestParam(required = false) String verdict,
      @RequestParam(required = false) String q,
      @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
    UUID tenantId = TenantContext.require().tenantId();
    Page<AuditLog> page;
    if (q != null && !q.isBlank()) {
      page = entries.search(tenantId, verdict, q, pageable);
    } else if (verdict != null) {
      page = entries.findByTenantIdAndVerdict(tenantId, verdict, pageable);
    } else {
      page = entries.findByTenantId(tenantId, pageable);
    }
    List<AuditView> views = page.getContent().stream().map(AuditView::of).toList();
    return new AuditPage(views, page.getNumber(), page.getSize(), page.getTotalElements());
  }

  /** Returns one of the tenant's audit entries, or 404 if it isn't theirs. */
  @GetMapping("/{id}")
  public AuditView get(@PathVariable Long id) {
    UUID tenantId = TenantContext.require().tenantId();
    return entries
        .findByIdAndTenantId(id, tenantId)
        .map(AuditView::of)
        .orElseThrow(() -> new NotFoundException("Audit entry " + id + " not found."));
  }

  /** Re-verifies the tenant's hash chain. */
  @GetMapping("/verify")
  public VerifyResult verify() {
    UUID tenantId = TenantContext.require().tenantId();
    return audit
        .firstBrokenLink(tenantId)
        .map(id -> new VerifyResult(false, id))
        .orElseGet(() -> new VerifyResult(true, null));
  }

  /**
   * GDPR subject-access export: every audit entry recorded for one data subject ({@code subject},
   * matched on the actor), oldest first and scoped to the tenant — the artifact for answering a
   * right-of-access (DSAR) request.
   */
  @GetMapping("/dsar")
  public DsarExport dsar(@RequestParam String subject) {
    UUID tenantId = TenantContext.require().tenantId();
    List<AuditView> views =
        entries.findByTenantIdAndActorOrderByIdAsc(tenantId, subject).stream()
            .map(AuditView::of)
            .toList();
    return new DsarExport(subject, views.size(), views);
  }

  /**
   * A page of the tenant's console management actions (key/member/policy changes), newest first —
   * the administrative counterpart to the AI-call audit trail, showing who changed what.
   */
  @GetMapping("/management")
  public ManagementAuditPage management(
      @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
    UUID tenantId = TenantContext.require().tenantId();
    Page<ManagementAudit> page = managementEntries.findByTenantId(tenantId, pageable);
    List<ManagementAuditView> views =
        page.getContent().stream().map(ManagementAuditView::of).toList();
    return new ManagementAuditPage(
        views, page.getNumber(), page.getSize(), page.getTotalElements());
  }

  /** A page of audit entries plus its paging metadata. */
  public record AuditPage(List<AuditView> entries, int page, int size, long total) {}

  /** A page of management-action entries plus its paging metadata. */
  public record ManagementAuditPage(
      List<ManagementAuditView> entries, int page, int size, long total) {}

  /** A GDPR subject-access export for one data subject. */
  public record DsarExport(String subject, int entryCount, List<AuditView> entries) {}

  /** The result of a chain verification: intact, or the id of the first broken link. */
  public record VerifyResult(boolean intact, Long firstBrokenId) {}
}

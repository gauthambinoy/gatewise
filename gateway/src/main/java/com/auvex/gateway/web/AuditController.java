package com.auvex.gateway.web;

import com.auvex.gateway.audit.AuditLog;
import com.auvex.gateway.audit.AuditLogRepository;
import com.auvex.gateway.audit.AuditService;
import com.auvex.gateway.auth.TenantContext;
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
  private final AuditService audit;

  public AuditController(AuditLogRepository entries, AuditService audit) {
    this.entries = entries;
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

  /** A page of audit entries plus its paging metadata. */
  public record AuditPage(List<AuditView> entries, int page, int size, long total) {}

  /** The result of a chain verification: intact, or the id of the first broken link. */
  public record VerifyResult(boolean intact, Long firstBrokenId) {}
}

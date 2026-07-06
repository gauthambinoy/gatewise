package com.gatewise.gateway.web;

import com.gatewise.gateway.audit.ManagementAuditService;
import com.gatewise.gateway.auth.TenantContext;
import com.gatewise.gateway.member.Member;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manage a tenant's console members over REST.
 *
 * <p>Every operation acts on the calling tenant (resolved from the API key), so a tenant only ever
 * sees and edits its own members.
 */
@RestController
@RequestMapping("/v1/members")
public class MemberController {

  private final MemberAdminService service;
  private final ManagementAuditService managementAudit;

  public MemberController(MemberAdminService service, ManagementAuditService managementAudit) {
    this.service = service;
    this.managementAudit = managementAudit;
  }

  /** Lists the tenant's members. */
  @GetMapping
  public List<MemberView> list() {
    return service.list(tenantId()).stream().map(MemberView::of).toList();
  }

  /** Invites a new member to the tenant. */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public MemberView create(@Valid @RequestBody MemberRequest request) {
    Member member = service.create(tenantId(), request);
    managementAudit.record("member.create", "member", member.getId(), member.getRole());
    return MemberView.of(member);
  }

  /** Returns one of the tenant's members, or 404 if it isn't theirs. */
  @GetMapping("/{id}")
  public MemberView get(@PathVariable UUID id) {
    return MemberView.of(service.get(tenantId(), id));
  }

  /** Updates one of the tenant's members (name, role, status). */
  @PutMapping("/{id}")
  public MemberView update(@PathVariable UUID id, @Valid @RequestBody MemberRequest request) {
    Member member = service.update(tenantId(), id, request);
    managementAudit.record("member.update", "member", id, member.getRole());
    return MemberView.of(member);
  }

  /** Removes one of the tenant's members. */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    service.delete(tenantId(), id);
    managementAudit.record("member.delete", "member", id, null);
  }

  private static UUID tenantId() {
    return TenantContext.require().tenantId();
  }
}

package com.auvex.gateway.web;

import com.auvex.gateway.auth.TenantContext;
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

  public MemberController(MemberAdminService service) {
    this.service = service;
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
    return MemberView.of(service.create(tenantId(), request));
  }

  /** Returns one of the tenant's members, or 404 if it isn't theirs. */
  @GetMapping("/{id}")
  public MemberView get(@PathVariable UUID id) {
    return MemberView.of(service.get(tenantId(), id));
  }

  /** Updates one of the tenant's members (name, role, status). */
  @PutMapping("/{id}")
  public MemberView update(@PathVariable UUID id, @Valid @RequestBody MemberRequest request) {
    return MemberView.of(service.update(tenantId(), id, request));
  }

  /** Removes one of the tenant's members. */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    service.delete(tenantId(), id);
  }

  private static UUID tenantId() {
    return TenantContext.require().tenantId();
  }
}

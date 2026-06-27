package com.auvex.gateway.web;

import com.auvex.gateway.audit.ManagementAuditService;
import com.auvex.gateway.auth.TenantContext;
import com.auvex.gateway.tenant.ApiKey;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manage a tenant's API keys over REST.
 *
 * <p>Acts on the calling tenant (resolved from the presented key). Creating a key returns its raw
 * secret once; afterwards only the masked view is available.
 */
@RestController
@RequestMapping("/v1/keys")
public class KeyController {

  private final KeyService service;
  private final ManagementAuditService managementAudit;

  public KeyController(KeyService service, ManagementAuditService managementAudit) {
    this.service = service;
    this.managementAudit = managementAudit;
  }

  /** Lists the tenant's keys (masked). */
  @GetMapping
  public List<KeyView> list() {
    return service.list(tenantId()).stream().map(KeyView::of).toList();
  }

  /** Creates a new key and returns its one-time secret. */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CreatedKeyView create(@RequestBody(required = false) CreateKeyRequest request) {
    KeyService.Created created =
        service.create(tenantId(), request == null ? null : request.name());
    ApiKey key = created.key();
    managementAudit.record("key.create", "api_key", key.getId(), key.getName());
    return new CreatedKeyView(
        key.getId(),
        key.getName(),
        key.getPrefix(),
        key.getStatus(),
        key.getCreatedAt(),
        created.rawKey());
  }

  /** Revokes one of the tenant's keys. */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revoke(@PathVariable UUID id) {
    service.revoke(tenantId(), id);
    managementAudit.record("key.revoke", "api_key", id, null);
  }

  private static UUID tenantId() {
    return TenantContext.require().tenantId();
  }
}

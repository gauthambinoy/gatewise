package com.auvex.gateway.web;

import com.auvex.gateway.audit.ManagementAuditService;
import com.auvex.gateway.auth.TenantContext;
import com.auvex.gateway.policy.Policy;
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
 * Manage a tenant's policy rules over REST.
 *
 * <p>Every operation acts on the calling tenant (resolved from the API key), so a tenant only ever
 * sees and edits its own rules.
 */
@RestController
@RequestMapping("/v1/policies")
public class PolicyController {

  private final PolicyAdminService service;
  private final ManagementAuditService managementAudit;

  public PolicyController(PolicyAdminService service, ManagementAuditService managementAudit) {
    this.service = service;
    this.managementAudit = managementAudit;
  }

  /** Lists the tenant's policy rules. */
  @GetMapping
  public List<PolicyView> list() {
    return service.list(tenantId()).stream().map(PolicyView::of).toList();
  }

  /** Creates a new policy rule for the tenant. */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public PolicyView create(@Valid @RequestBody PolicyRequest request) {
    Policy policy = service.create(tenantId(), request);
    managementAudit.record("policy.create", "policy", policy.getId(), policy.getName());
    return PolicyView.of(policy);
  }

  /** Returns one of the tenant's rules, or 404 if it isn't theirs. */
  @GetMapping("/{id}")
  public PolicyView get(@PathVariable UUID id) {
    return PolicyView.of(service.get(tenantId(), id));
  }

  /** Replaces one of the tenant's rules. */
  @PutMapping("/{id}")
  public PolicyView update(@PathVariable UUID id, @Valid @RequestBody PolicyRequest request) {
    Policy policy = service.update(tenantId(), id, request);
    managementAudit.record("policy.update", "policy", id, policy.getName());
    return PolicyView.of(policy);
  }

  /** Deletes one of the tenant's rules. */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    service.delete(tenantId(), id);
    managementAudit.record("policy.delete", "policy", id, null);
  }

  private static UUID tenantId() {
    return TenantContext.require().tenantId();
  }
}

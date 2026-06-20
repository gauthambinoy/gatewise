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
 * Manage a tenant's policy rules over REST.
 *
 * <p>Every operation acts on the calling tenant (resolved from the API key), so a tenant only ever
 * sees and edits its own rules.
 */
@RestController
@RequestMapping("/v1/policies")
public class PolicyController {

  private final PolicyAdminService service;

  public PolicyController(PolicyAdminService service) {
    this.service = service;
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
    return PolicyView.of(service.create(tenantId(), request));
  }

  /** Returns one of the tenant's rules, or 404 if it isn't theirs. */
  @GetMapping("/{id}")
  public PolicyView get(@PathVariable UUID id) {
    return PolicyView.of(service.get(tenantId(), id));
  }

  /** Replaces one of the tenant's rules. */
  @PutMapping("/{id}")
  public PolicyView update(@PathVariable UUID id, @Valid @RequestBody PolicyRequest request) {
    return PolicyView.of(service.update(tenantId(), id, request));
  }

  /** Deletes one of the tenant's rules. */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    service.delete(tenantId(), id);
  }

  private static UUID tenantId() {
    return TenantContext.require().tenantId();
  }
}

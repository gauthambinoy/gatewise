package com.gatewise.gateway.web;

import com.gatewise.gateway.policy.Policy;
import com.gatewise.gateway.policy.PolicyRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The application service behind the policy CRUD API.
 *
 * <p>Every operation is scoped to a tenant id, and single-rule lookups go through {@code
 * findByIdAndTenantId}, so one tenant can never read or change another's rules — a miss is a 404,
 * not a leak of whether the id exists.
 */
@Component
public class PolicyAdminService {

  private final PolicyRepository policies;

  public PolicyAdminService(PolicyRepository policies) {
    this.policies = policies;
  }

  public List<Policy> list(UUID tenantId) {
    return policies.findByTenantId(tenantId);
  }

  public Policy create(UUID tenantId, PolicyRequest request) {
    Policy policy =
        new Policy(
            tenantId,
            request.name(),
            request.effect(),
            request.resourceType(),
            request.resourceValue(),
            priorityOf(request),
            enabledOf(request));
    return policies.save(policy);
  }

  public Policy get(UUID tenantId, UUID id) {
    return policies
        .findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new NotFoundException("Policy " + id + " not found."));
  }

  public Policy update(UUID tenantId, UUID id, PolicyRequest request) {
    Policy policy = get(tenantId, id);
    policy.update(
        request.name(),
        request.effect(),
        request.resourceType(),
        request.resourceValue(),
        priorityOf(request),
        enabledOf(request));
    return policies.save(policy);
  }

  public void delete(UUID tenantId, UUID id) {
    policies.delete(get(tenantId, id));
  }

  private static int priorityOf(PolicyRequest request) {
    return request.priority() == null ? 100 : request.priority();
  }

  private static boolean enabledOf(PolicyRequest request) {
    return request.enabled() == null || request.enabled();
  }
}

package com.gatewise.gateway;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gatewise.gateway.audit.AuditEntry;
import com.gatewise.gateway.audit.AuditService;
import com.gatewise.gateway.audit.Verdict;
import com.gatewise.gateway.auth.ApiKeyHasher;
import com.gatewise.gateway.support.AbstractPostgresIntegrationTest;
import com.gatewise.gateway.tenant.ApiKey;
import com.gatewise.gateway.tenant.ApiKeyRepository;
import com.gatewise.gateway.tenant.Tenant;
import com.gatewise.gateway.tenant.TenantRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Proves /v1/usage/users aggregates the audit trail per actor. */
@AutoConfigureMockMvc
class PerUserUsageIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private AuditService audit;
  @Autowired private TenantRepository tenants;
  @Autowired private ApiKeyRepository apiKeys;

  private void append(UUID tenantId, String actor, Verdict verdict) {
    audit.append(
        new AuditEntry(
            tenantId, UUID.randomUUID(), actor, "model-x", verdict, "prompt", null, Instant.now()));
  }

  @Test
  void aggregatesUsagePerUser() throws Exception {
    Tenant tenant = tenants.save(new Tenant("Acme", "acme-" + UUID.randomUUID()));
    String raw = "gatewise_" + UUID.randomUUID().toString().replace("-", "");
    apiKeys.save(
        new ApiKey(tenant.getId(), "default", ApiKeyHasher.hash(raw), raw.substring(0, 12)));

    append(tenant.getId(), "alice", Verdict.ALLOWED);
    append(tenant.getId(), "alice", Verdict.REDACTED);
    append(tenant.getId(), "bob", Verdict.BLOCKED);

    mvc.perform(get("/v1/usage/users").header("Authorization", "Bearer " + raw))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[?(@.actor=='alice')].requests", contains(2)))
        .andExpect(jsonPath("$[?(@.actor=='alice')].redacted", contains(1)))
        .andExpect(jsonPath("$[?(@.actor=='bob')].blocked", contains(1)));
  }
}

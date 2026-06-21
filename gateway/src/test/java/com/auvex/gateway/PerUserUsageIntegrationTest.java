package com.auvex.gateway;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.auvex.gateway.audit.AuditEntry;
import com.auvex.gateway.audit.AuditService;
import com.auvex.gateway.audit.Verdict;
import com.auvex.gateway.auth.ApiKeyHasher;
import com.auvex.gateway.support.AbstractPostgresIntegrationTest;
import com.auvex.gateway.tenant.ApiKey;
import com.auvex.gateway.tenant.ApiKeyRepository;
import com.auvex.gateway.tenant.Tenant;
import com.auvex.gateway.tenant.TenantRepository;
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
    String raw = "auvex_" + UUID.randomUUID().toString().replace("-", "");
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

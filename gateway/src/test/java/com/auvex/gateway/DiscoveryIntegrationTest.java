package com.auvex.gateway;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.auvex.gateway.audit.AuditEntry;
import com.auvex.gateway.audit.AuditSink;
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

/** Proves shadow-AI discovery flags a model that isn't in the routing allow-list. */
@AutoConfigureMockMvc
class DiscoveryIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantRepository tenants;
  @Autowired private ApiKeyRepository apiKeys;
  @Autowired private AuditSink auditSink;

  @Test
  void flagsUnsanctionedModelAsShadow() throws Exception {
    Tenant tenant = tenants.save(new Tenant("Acme", "acme-" + UUID.randomUUID()));
    String raw = "auvex_" + UUID.randomUUID().toString().replace("-", "");
    apiKeys.save(
        new ApiKey(tenant.getId(), "default", ApiKeyHasher.hash(raw), raw.substring(0, 12)));

    // A sanctioned model (a routing target) and an un-sanctioned one (not in the allow-list).
    record(tenant.getId(), "openai/gpt-oss-120b:free");
    record(tenant.getId(), "openai/gpt-oss-120b:free");
    record(tenant.getId(), "cohere/command-r");

    mvc.perform(get("/v1/discovery").header("Authorization", "Bearer " + raw))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.distinctModels").value(2))
        .andExpect(jsonPath("$.shadowModelCount").value(1))
        .andExpect(jsonPath("$.shadowModels[0].model").value("cohere/command-r"))
        .andExpect(jsonPath("$.shadowModels[0].provider").value("cohere"))
        .andExpect(jsonPath("$.shadowModels[0].sanctioned").value(false));
  }

  private void record(UUID tenantId, String model) {
    auditSink.record(
        new AuditEntry(
            tenantId,
            UUID.randomUUID(),
            "alice",
            model,
            Verdict.ALLOWED,
            "hello",
            null,
            Instant.now()));
  }
}

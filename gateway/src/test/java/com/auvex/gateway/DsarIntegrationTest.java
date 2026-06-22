package com.auvex.gateway;

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

/** Proves the GDPR DSAR export returns only the requested subject's entries, tenant-scoped. */
@AutoConfigureMockMvc
class DsarIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantRepository tenants;
  @Autowired private ApiKeyRepository apiKeys;
  @Autowired private AuditService audit;

  @Test
  void exportsOnlyTheSubjectsEntries() throws Exception {
    Tenant tenant = tenants.save(new Tenant("Acme", "acme-" + UUID.randomUUID()));
    String raw = "auvex_" + UUID.randomUUID().toString().replace("-", "");
    apiKeys.save(
        new ApiKey(tenant.getId(), "default", ApiKeyHasher.hash(raw), raw.substring(0, 12)));

    audit.append(entry(tenant.getId(), "alice"));
    audit.append(entry(tenant.getId(), "alice"));
    audit.append(entry(tenant.getId(), "bob"));

    mvc.perform(
            get("/v1/audit/dsar")
                .param("subject", "alice")
                .header("Authorization", "Bearer " + raw))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.subject").value("alice"))
        .andExpect(jsonPath("$.entryCount").value(2))
        .andExpect(jsonPath("$.entries[0].actor").value("alice"))
        .andExpect(jsonPath("$.entries[1].actor").value("alice"));
  }

  private static AuditEntry entry(UUID tenantId, String actor) {
    return new AuditEntry(
        tenantId,
        UUID.randomUUID(),
        actor,
        "model-x",
        Verdict.ALLOWED,
        "prompt",
        null,
        Instant.now());
  }
}

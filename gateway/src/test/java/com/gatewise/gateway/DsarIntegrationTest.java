package com.gatewise.gateway;

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
    String raw = "gatewise_" + UUID.randomUUID().toString().replace("-", "");
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

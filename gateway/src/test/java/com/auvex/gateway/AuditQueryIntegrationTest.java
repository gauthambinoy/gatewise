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

/** Exercises the audit query + verify API: tenant isolation, verdict filtering, chain integrity. */
@AutoConfigureMockMvc
class AuditQueryIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private AuditService audit;
  @Autowired private TenantRepository tenants;
  @Autowired private ApiKeyRepository apiKeys;

  private record TenantAuth(UUID tenantId, String key) {}

  private TenantAuth newTenant() {
    Tenant t = tenants.save(new Tenant("Acme", "acme-" + UUID.randomUUID()));
    String raw = "auvex_" + UUID.randomUUID().toString().replace("-", "");
    apiKeys.save(new ApiKey(t.getId(), "default", ApiKeyHasher.hash(raw), raw.substring(0, 12)));
    return new TenantAuth(t.getId(), raw);
  }

  private void appendEntry(UUID tenantId, Verdict verdict) {
    audit.append(
        new AuditEntry(
            tenantId, UUID.randomUUID(), "svc", "model-x", verdict, "prompt", null, Instant.now()));
  }

  @Test
  void returnsOnlyTheCallersEntries() throws Exception {
    TenantAuth a = newTenant();
    appendEntry(a.tenantId(), Verdict.ALLOWED);
    appendEntry(a.tenantId(), Verdict.REDACTED);
    TenantAuth b = newTenant();
    appendEntry(b.tenantId(), Verdict.ALLOWED);

    mvc.perform(get("/v1/audit").header("Authorization", "Bearer " + a.key()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.entries.length()").value(2));
  }

  @Test
  void filtersByVerdict() throws Exception {
    TenantAuth a = newTenant();
    appendEntry(a.tenantId(), Verdict.ALLOWED);
    appendEntry(a.tenantId(), Verdict.BLOCKED);

    mvc.perform(
            get("/v1/audit")
                .param("verdict", "blocked")
                .header("Authorization", "Bearer " + a.key()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.entries[0].verdict").value("blocked"));
  }

  @Test
  void verifyReportsAnIntactChain() throws Exception {
    TenantAuth a = newTenant();
    appendEntry(a.tenantId(), Verdict.ALLOWED);
    appendEntry(a.tenantId(), Verdict.ALLOWED);

    mvc.perform(get("/v1/audit/verify").header("Authorization", "Bearer " + a.key()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.intact").value(true));
  }
}

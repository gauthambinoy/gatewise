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

/** Verifies the usage summary aggregates a tenant's audit entries, and only that tenant's. */
@AutoConfigureMockMvc
class UsageMetricsIntegrationTest extends AbstractPostgresIntegrationTest {

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

  private void appendEntry(UUID tenantId, Verdict verdict, String model) {
    audit.append(
        new AuditEntry(
            tenantId, UUID.randomUUID(), "svc", model, verdict, "prompt", null, Instant.now()));
  }

  @Test
  void summarizesOnlyTheCallersUsage() throws Exception {
    TenantAuth a = newTenant();
    appendEntry(a.tenantId(), Verdict.ALLOWED, "model-x");
    appendEntry(a.tenantId(), Verdict.ALLOWED, "model-x");
    appendEntry(a.tenantId(), Verdict.BLOCKED, "model-y");

    // Another tenant's traffic must not leak into A's summary.
    TenantAuth b = newTenant();
    appendEntry(b.tenantId(), Verdict.ALLOWED, "model-x");

    mvc.perform(get("/v1/usage").header("Authorization", "Bearer " + a.key()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalCalls").value(3))
        .andExpect(jsonPath("$.allowed").value(2))
        .andExpect(jsonPath("$.blocked").value(1))
        .andExpect(jsonPath("$.byModel['model-x']").value(2))
        .andExpect(jsonPath("$.byModel['model-y']").value(1));
  }
}

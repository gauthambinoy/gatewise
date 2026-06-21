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
import com.jayway.jsonpath.JsonPath;
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
    appendEntry(tenantId, verdict, "prompt", "model-x");
  }

  private void appendEntry(UUID tenantId, Verdict verdict, String prompt, String model) {
    audit.append(
        new AuditEntry(
            tenantId, UUID.randomUUID(), "svc", model, verdict, prompt, null, Instant.now()));
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
  void searchesByFreeText() throws Exception {
    TenantAuth a = newTenant();
    appendEntry(a.tenantId(), Verdict.ALLOWED, "summarize the invoice", "gpt-4o");
    appendEntry(a.tenantId(), Verdict.ALLOWED, "draft an offer letter", "claude-3");

    mvc.perform(get("/v1/audit").param("q", "INVOICE").header("Authorization", "Bearer " + a.key()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.entries[0].promptRedacted").value("summarize the invoice"));

    mvc.perform(get("/v1/audit").param("q", "claude").header("Authorization", "Bearer " + a.key()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.entries[0].model").value("claude-3"));
  }

  @Test
  void fetchesASingleEntryById() throws Exception {
    TenantAuth a = newTenant();
    appendEntry(a.tenantId(), Verdict.REDACTED, "one specific prompt", "gpt-4o");

    String list =
        mvc.perform(get("/v1/audit").header("Authorization", "Bearer " + a.key()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    int id = JsonPath.read(list, "$.entries[0].id");

    mvc.perform(get("/v1/audit/" + id).header("Authorization", "Bearer " + a.key()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.promptRedacted").value("one specific prompt"))
        .andExpect(jsonPath("$.model").value("gpt-4o"));
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

package com.auvex.gateway;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.auvex.gateway.auth.ApiKeyHasher;
import com.auvex.gateway.support.AbstractPostgresIntegrationTest;
import com.auvex.gateway.tenant.ApiKey;
import com.auvex.gateway.tenant.ApiKeyRepository;
import com.auvex.gateway.tenant.Tenant;
import com.auvex.gateway.tenant.TenantRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Exercises the management-action audit trail end to end: recording on write, tenant isolation. */
@AutoConfigureMockMvc
class ManagementAuditIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantRepository tenants;
  @Autowired private ApiKeyRepository apiKeys;

  // A fresh tenant whose only key is created directly (so it isn't itself a recorded action).
  private String newTenantKey() {
    Tenant t = tenants.save(new Tenant("Acme", "acme-" + UUID.randomUUID()));
    String raw = "auvex_" + UUID.randomUUID().toString().replace("-", "");
    apiKeys.save(new ApiKey(t.getId(), "default", ApiKeyHasher.hash(raw), raw.substring(0, 12)));
    return raw;
  }

  @Test
  void recordsKeyCreationAgainstTheApiKeyPrincipal() throws Exception {
    String key = newTenantKey();

    mvc.perform(
            post("/v1/keys")
                .header("Authorization", "Bearer " + key)
                .contentType("application/json")
                .content("{\"name\":\"ci-key\"}"))
        .andExpect(status().isCreated());

    mvc.perform(get("/v1/audit/management").header("Authorization", "Bearer " + key))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.entries[0].action").value("key.create"))
        .andExpect(jsonPath("$.entries[0].resourceType").value("api_key"))
        .andExpect(jsonPath("$.entries[0].principalType").value("api_key"));
  }

  @Test
  void scopesTheManagementTrailToTheCallingTenant() throws Exception {
    String a = newTenantKey();
    String b = newTenantKey();

    mvc.perform(
            post("/v1/keys")
                .header("Authorization", "Bearer " + a)
                .contentType("application/json")
                .content("{\"name\":\"a-key\"}"))
        .andExpect(status().isCreated());

    // Tenant b created nothing through the API, so its management trail is empty.
    mvc.perform(get("/v1/audit/management").header("Authorization", "Bearer " + b))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0));
  }
}

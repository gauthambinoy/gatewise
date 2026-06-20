package com.auvex.gateway;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.auvex.gateway.auth.ApiKeyHasher;
import com.auvex.gateway.support.AbstractPostgresIntegrationTest;
import com.auvex.gateway.tenant.ApiKey;
import com.auvex.gateway.tenant.ApiKeyRepository;
import com.auvex.gateway.tenant.Tenant;
import com.auvex.gateway.tenant.TenantRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Proves API-key authentication and tenant isolation end to end against a real database. */
@AutoConfigureMockMvc
class ApiKeyAuthIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantRepository tenants;
  @Autowired private ApiKeyRepository apiKeys;

  // Persists a tenant plus an active key for it, returning the raw key to present.
  private String newTenantWithKey(Tenant tenant) {
    tenants.save(tenant);
    String raw = "auvex_" + UUID.randomUUID().toString().replace("-", "");
    apiKeys.save(
        new ApiKey(tenant.getId(), "default", ApiKeyHasher.hash(raw), raw.substring(0, 12)));
    return raw;
  }

  @Test
  void validKeyResolvesItsTenant() throws Exception { // T09
    Tenant t = new Tenant("Acme", "acme-" + UUID.randomUUID());
    String raw = newTenantWithKey(t);

    mvc.perform(get("/v1/me").header("Authorization", "Bearer " + raw))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(t.getId().toString()))
        .andExpect(jsonPath("$.slug").value(t.getSlug()));
  }

  @Test
  void missingOrInvalidKeyIsRejected() throws Exception { // T10
    mvc.perform(get("/v1/me")).andExpect(status().isUnauthorized());
    mvc.perform(get("/v1/me").header("Authorization", "Bearer not-a-real-key"))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/v1/me").header("Authorization", "Token abc"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void eachKeyResolvesOnlyItsOwnTenant() throws Exception { // T11 — cross-tenant isolation
    Tenant a = new Tenant("Tenant A", "a-" + UUID.randomUUID());
    Tenant b = new Tenant("Tenant B", "b-" + UUID.randomUUID());
    String rawA = newTenantWithKey(a);
    String rawB = newTenantWithKey(b);

    mvc.perform(get("/v1/me").header("Authorization", "Bearer " + rawA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slug").value(a.getSlug()))
        .andExpect(jsonPath("$.slug").value(not(b.getSlug())));
    mvc.perform(get("/v1/me").header("Authorization", "Bearer " + rawB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slug").value(b.getSlug()));
  }

  @Test
  void revokedKeyIsRejected() throws Exception {
    Tenant t = new Tenant("Revoked Co", "rev-" + UUID.randomUUID());
    tenants.save(t);
    String raw = "auvex_" + UUID.randomUUID().toString().replace("-", "");
    ApiKey key = new ApiKey(t.getId(), "default", ApiKeyHasher.hash(raw), raw.substring(0, 12));
    key.revoke();
    apiKeys.save(key);

    mvc.perform(get("/v1/me").header("Authorization", "Bearer " + raw))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void expiredKeyIsRejected() throws Exception {
    Tenant t = new Tenant("Expired Co", "exp-" + UUID.randomUUID());
    tenants.save(t);
    String raw = "auvex_" + UUID.randomUUID().toString().replace("-", "");
    ApiKey key = new ApiKey(t.getId(), "default", ApiKeyHasher.hash(raw), raw.substring(0, 12));
    key.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
    apiKeys.save(key);

    mvc.perform(get("/v1/me").header("Authorization", "Bearer " + raw))
        .andExpect(status().isUnauthorized());
  }
}

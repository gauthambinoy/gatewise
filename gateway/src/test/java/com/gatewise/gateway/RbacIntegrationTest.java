package com.gatewise.gateway;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gatewise.gateway.auth.ApiKeyHasher;
import com.gatewise.gateway.auth.ConsoleSession;
import com.gatewise.gateway.auth.ConsoleSessionService;
import com.gatewise.gateway.support.AbstractPostgresIntegrationTest;
import com.gatewise.gateway.tenant.ApiKey;
import com.gatewise.gateway.tenant.ApiKeyRepository;
import com.gatewise.gateway.tenant.Tenant;
import com.gatewise.gateway.tenant.TenantRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Proves RBAC: management endpoints need a console session + role; AI endpoints stay API-key. */
@AutoConfigureMockMvc
class RbacIntegrationTest extends AbstractPostgresIntegrationTest {

  @DynamicPropertySource
  static void rbac(DynamicPropertyRegistry registry) {
    registry.add("gatewise.rbac.enabled", () -> true);
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantRepository tenants;
  @Autowired private ApiKeyRepository apiKeys;
  @Autowired private ConsoleSessionService sessions;

  private UUID tenantId;
  private String apiKey;

  private void seed() {
    Tenant t = tenants.save(new Tenant("Acme", "acme-" + UUID.randomUUID()));
    tenantId = t.getId();
    String raw = "gatewise_" + UUID.randomUUID().toString().replace("-", "");
    apiKeys.save(new ApiKey(tenantId, "default", ApiKeyHasher.hash(raw), raw.substring(0, 12)));
    apiKey = raw;
  }

  private String sessionToken(String role) {
    return sessions.mint(
        new ConsoleSession(
            tenantId,
            UUID.randomUUID(),
            role + "@acme.io",
            role,
            Instant.now().plus(Duration.ofHours(1))));
  }

  @Test
  void apiKeyIsRejectedOnManagementEndpoints() throws Exception {
    seed();
    mvc.perform(get("/v1/policies").header("Authorization", "Bearer " + apiKey))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void auditorCanReadButNotWritePolicies() throws Exception {
    seed();
    String auditor = sessionToken("auditor");
    mvc.perform(get("/v1/policies").header("Authorization", "Bearer " + auditor))
        .andExpect(status().isOk());
    mvc.perform(
            post("/v1/policies")
                .header("Authorization", "Bearer " + auditor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"x\",\"effect\":\"allow\",\"resourceType\":\"model\","
                        + "\"resourceValue\":\"*\",\"priority\":1,\"enabled\":true}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void securityAdminCanWritePolicies() throws Exception {
    seed();
    String admin = sessionToken("security_admin");
    mvc.perform(
            post("/v1/policies")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"x\",\"effect\":\"allow\",\"resourceType\":\"model\","
                        + "\"resourceValue\":\"*\",\"priority\":1,\"enabled\":true}"))
        .andExpect(status().isCreated());
  }

  @Test
  void onlyOwnerCanWriteMembers() throws Exception {
    seed();
    String admin = sessionToken("security_admin");
    mvc.perform(
            post("/v1/members")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"new@acme.io\",\"name\":\"New\",\"role\":\"auditor\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void aiEndpointsStayApiKeyAuthenticated() throws Exception {
    seed();
    // RBAC doesn't touch the AI surface: the API key still works on /v1/moderations.
    mvc.perform(
            post("/v1/moderations")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"input\":\"hello\"}"))
        .andExpect(status().isOk());
  }
}

package com.auvex.gateway;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Exercises the API-key management endpoints and the routing/models listing. */
@AutoConfigureMockMvc
class KeyManagementIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantRepository tenants;
  @Autowired private ApiKeyRepository apiKeys;

  private String bootstrapKey() {
    Tenant t = tenants.save(new Tenant("Acme", "acme-" + UUID.randomUUID()));
    String raw = "auvex_" + UUID.randomUUID().toString().replace("-", "");
    apiKeys.save(new ApiKey(t.getId(), "default", ApiKeyHasher.hash(raw), raw.substring(0, 12)));
    return raw;
  }

  @Test
  void createdKeyAuthenticatesUntilRevoked() throws Exception {
    String admin = bootstrapKey();

    String created =
        mvc.perform(
                post("/v1/keys")
                    .header("Authorization", "Bearer " + admin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"app-key\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.secret").value(startsWith("auvex_sk_")))
            .andExpect(jsonPath("$.status").value("active"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String secret = JsonPath.read(created, "$.secret");
    String id = JsonPath.read(created, "$.id");

    // The freshly minted key works.
    mvc.perform(get("/v1/me").header("Authorization", "Bearer " + secret))
        .andExpect(status().isOk());

    mvc.perform(get("/v1/keys").header("Authorization", "Bearer " + admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(2)));

    mvc.perform(delete("/v1/keys/" + id).header("Authorization", "Bearer " + admin))
        .andExpect(status().isNoContent());

    // Once revoked it no longer authenticates.
    mvc.perform(get("/v1/me").header("Authorization", "Bearer " + secret))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void listsTheRoutingTable() throws Exception {
    String admin = bootstrapKey();
    mvc.perform(get("/v1/models").header("Authorization", "Bearer " + admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.alias=='smart')].alias", contains("smart")));
  }
}

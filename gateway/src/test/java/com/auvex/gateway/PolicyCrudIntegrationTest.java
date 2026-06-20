package com.auvex.gateway;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.auvex.gateway.auth.ApiKeyHasher;
import com.auvex.gateway.support.AbstractPostgresIntegrationTest;
import com.auvex.gateway.tenant.ApiKey;
import com.auvex.gateway.tenant.ApiKeyRepository;
import com.auvex.gateway.tenant.Tenant;
import com.auvex.gateway.tenant.TenantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Exercises the policy management API: CRUD, validation, and strict per-tenant isolation. */
@AutoConfigureMockMvc
class PolicyCrudIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantRepository tenants;
  @Autowired private ApiKeyRepository apiKeys;
  @Autowired private ObjectMapper json;

  private static final String DENY_CARDS =
      "{\"name\":\"block-cards\",\"effect\":\"deny\","
          + "\"resourceType\":\"data_type\",\"resourceValue\":\"credit_card\",\"priority\":100}";

  private String authKey() {
    Tenant t = tenants.save(new Tenant("Acme", "acme-" + UUID.randomUUID()));
    String raw = "auvex_" + UUID.randomUUID().toString().replace("-", "");
    apiKeys.save(new ApiKey(t.getId(), "default", ApiKeyHasher.hash(raw), raw.substring(0, 12)));
    return raw;
  }

  private String createPolicy(String key, String body) throws Exception {
    String response =
        mvc.perform(
                post("/v1/policies")
                    .header("Authorization", "Bearer " + key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(response).get("id").asText();
  }

  @Test
  void createThenListRoundtrip() throws Exception {
    String key = authKey();
    createPolicy(key, DENY_CARDS);

    mvc.perform(get("/v1/policies").header("Authorization", "Bearer " + key))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("block-cards"))
        .andExpect(jsonPath("$[0].effect").value("deny"));
  }

  @Test
  void validationRejectsAnUnknownEffect() throws Exception {
    String key = authKey();
    String bad =
        "{\"name\":\"x\",\"effect\":\"maybe\",\"resourceType\":\"model\",\"resourceValue\":\"*\"}";

    mvc.perform(
            post("/v1/policies")
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bad))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getUpdateDeleteRoundtrip() throws Exception {
    String key = authKey();
    String id =
        createPolicy(
            key,
            "{\"name\":\"allow-all\",\"effect\":\"allow\",\"resourceType\":\"model\","
                + "\"resourceValue\":\"*\"}");

    mvc.perform(get("/v1/policies/" + id).header("Authorization", "Bearer " + key))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.effect").value("allow"));

    mvc.perform(
            put("/v1/policies/" + id)
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"allow-all\",\"effect\":\"deny\",\"resourceType\":\"model\","
                        + "\"resourceValue\":\"*\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.effect").value("deny"));

    mvc.perform(delete("/v1/policies/" + id).header("Authorization", "Bearer " + key))
        .andExpect(status().isNoContent());

    mvc.perform(get("/v1/policies/" + id).header("Authorization", "Bearer " + key))
        .andExpect(status().isNotFound());
  }

  @Test
  void aTenantCannotSeeOrTouchAnotherTenantsPolicy() throws Exception {
    String keyA = authKey();
    String id = createPolicy(keyA, DENY_CARDS);

    String keyB = authKey();
    // B can't read A's rule...
    mvc.perform(get("/v1/policies/" + id).header("Authorization", "Bearer " + keyB))
        .andExpect(status().isNotFound());
    // ...can't delete it...
    mvc.perform(delete("/v1/policies/" + id).header("Authorization", "Bearer " + keyB))
        .andExpect(status().isNotFound());
    // ...and doesn't see it in its own (empty) list.
    mvc.perform(get("/v1/policies").header("Authorization", "Bearer " + keyB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }
}

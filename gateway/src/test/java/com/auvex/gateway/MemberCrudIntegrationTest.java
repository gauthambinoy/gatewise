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
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises the member CRUD API: lifecycle, role updates, tenant isolation, duplicate rejection.
 */
@AutoConfigureMockMvc
class MemberCrudIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantRepository tenants;
  @Autowired private ApiKeyRepository apiKeys;

  private String newTenantKey() {
    Tenant t = tenants.save(new Tenant("Acme", "acme-" + UUID.randomUUID()));
    String raw = "auvex_" + UUID.randomUUID().toString().replace("-", "");
    apiKeys.save(new ApiKey(t.getId(), "default", ApiKeyHasher.hash(raw), raw.substring(0, 12)));
    return raw;
  }

  @Test
  void inviteUpdateAndRemoveAMember() throws Exception {
    String key = newTenantKey();

    String created =
        mvc.perform(
                post("/v1/members")
                    .header("Authorization", "Bearer " + key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"maya@acme.com\",\"name\":\"Maya\",\"role\":\"owner\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.role").value("owner"))
            .andExpect(jsonPath("$.status").value("invited"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = JsonPath.read(created, "$.id");

    mvc.perform(get("/v1/members").header("Authorization", "Bearer " + key))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));

    mvc.perform(
            put("/v1/members/" + id)
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"maya@acme.com\",\"name\":\"Maya R\","
                        + "\"role\":\"security_admin\",\"status\":\"active\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("security_admin"))
        .andExpect(jsonPath("$.status").value("active"));

    mvc.perform(delete("/v1/members/" + id).header("Authorization", "Bearer " + key))
        .andExpect(status().isNoContent());

    mvc.perform(get("/v1/members").header("Authorization", "Bearer " + key))
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void rejectsADuplicateEmail() throws Exception {
    String key = newTenantKey();
    String body = "{\"email\":\"dup@acme.com\",\"role\":\"auditor\"}";
    mvc.perform(
            post("/v1/members")
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/v1/members")
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void doesNotLeakAnotherTenantsMembers() throws Exception {
    String a = newTenantKey();
    mvc.perform(
            post("/v1/members")
                .header("Authorization", "Bearer " + a)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@acme.com\",\"role\":\"owner\"}"))
        .andExpect(status().isCreated());

    String b = newTenantKey();
    mvc.perform(get("/v1/members").header("Authorization", "Bearer " + b))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }
}

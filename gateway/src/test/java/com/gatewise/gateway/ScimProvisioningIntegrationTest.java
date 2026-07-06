package com.gatewise.gateway;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gatewise.gateway.member.Member;
import com.gatewise.gateway.member.MemberRepository;
import com.gatewise.gateway.support.AbstractPostgresIntegrationTest;
import com.gatewise.gateway.tenant.Tenant;
import com.gatewise.gateway.tenant.TenantRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises the SCIM 2.0 Users API end-to-end: the create → list → filter → get → replace →
 * deactivate → delete lifecycle, the bearer-token gate, and the SCIM JSON shape.
 */
@AutoConfigureMockMvc
class ScimProvisioningIntegrationTest extends AbstractPostgresIntegrationTest {

  private static final String TENANT_SLUG = "scim-tenant";
  private static final String TOKEN = "scim-secret-token";
  private static final String AUTH = "Bearer " + TOKEN;
  private static final MediaType SCIM_JSON = MediaType.valueOf("application/scim+json");
  private static final String USER_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:User";
  private static final String LIST_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:ListResponse";
  private static final String ERROR_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:Error";

  @DynamicPropertySource
  static void scim(DynamicPropertyRegistry registry) {
    registry.add("gatewise.scim.token", () -> TOKEN);
    registry.add("gatewise.scim.tenant-slug", () -> TENANT_SLUG);
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantRepository tenants;
  @Autowired private MemberRepository members;

  private UUID tenantId;

  @BeforeEach
  void cleanTenant() {
    tenantId =
        tenants
            .findBySlug(TENANT_SLUG)
            .orElseGet(() -> tenants.save(new Tenant("SCIM Tenant", TENANT_SLUG)))
            .getId();
    List<Member> existing = members.findByTenantIdOrderByCreatedAtAsc(tenantId);
    members.deleteAll(existing);
  }

  @Test
  void fullUserLifecycle() throws Exception {
    String created =
        mvc.perform(
                post("/scim/v2/Users")
                    .header("Authorization", AUTH)
                    .contentType(SCIM_JSON)
                    .content(
                        "{\"schemas\":[\""
                            + USER_SCHEMA
                            + "\"],"
                            + "\"userName\":\"jdoe@corp.example\","
                            + "\"name\":{\"formatted\":\"J Doe\"},\"active\":true}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.schemas[0]").value(USER_SCHEMA))
            .andExpect(jsonPath("$.userName").value("jdoe@corp.example"))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.displayName").value("J Doe"))
            .andExpect(jsonPath("$.meta.resourceType").value("User"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = JsonPath.read(created, "$.id");

    // List: SCIM ListResponse with one result.
    mvc.perform(get("/scim/v2/Users").header("Authorization", AUTH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schemas[0]").value(LIST_SCHEMA))
        .andExpect(jsonPath("$.totalResults").value(1))
        .andExpect(jsonPath("$.Resources[0].userName").value("jdoe@corp.example"));

    // Filter by userName.
    mvc.perform(
            get("/scim/v2/Users")
                .header("Authorization", AUTH)
                .param("filter", "userName eq \"jdoe@corp.example\""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalResults").value(1))
        .andExpect(jsonPath("$.Resources[0].id").value(id));

    // Filter miss → empty.
    mvc.perform(
            get("/scim/v2/Users")
                .header("Authorization", AUTH)
                .param("filter", "userName eq \"nobody@corp.example\""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalResults").value(0));

    // Get by id.
    mvc.perform(get("/scim/v2/Users/" + id).header("Authorization", AUTH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userName").value("jdoe@corp.example"));

    // Replace (PUT).
    mvc.perform(
            put("/scim/v2/Users/" + id)
                .header("Authorization", AUTH)
                .contentType(SCIM_JSON)
                .content(
                    "{\"schemas\":[\""
                        + USER_SCHEMA
                        + "\"],"
                        + "\"userName\":\"jdoe@corp.example\","
                        + "\"name\":{\"formatted\":\"Jane Doe\"},\"active\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("Jane Doe"));

    // Deactivate via PATCH.
    mvc.perform(
            patch("/scim/v2/Users/" + id)
                .header("Authorization", AUTH)
                .contentType(SCIM_JSON)
                .content(
                    "{\"schemas\":[\"urn:ietf:params:scim:api:messages:2.0:PatchOp\"],"
                        + "\"Operations\":[{\"op\":\"replace\",\"path\":\"active\","
                        + "\"value\":false}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));

    // Delete.
    mvc.perform(delete("/scim/v2/Users/" + id).header("Authorization", AUTH))
        .andExpect(status().isNoContent());

    mvc.perform(get("/scim/v2/Users").header("Authorization", AUTH))
        .andExpect(jsonPath("$.totalResults").value(0));
  }

  @Test
  void rejectsAMissingTokenWithScimError() throws Exception {
    mvc.perform(get("/scim/v2/Users"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.schemas[0]").value(ERROR_SCHEMA))
        .andExpect(jsonPath("$.status").value("401"));
  }

  @Test
  void rejectsAWrongTokenWith401() throws Exception {
    mvc.perform(get("/scim/v2/Users").header("Authorization", "Bearer nope"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.schemas[0]").value(ERROR_SCHEMA));
  }

  @Test
  void rejectsADuplicateUserNameWith409() throws Exception {
    String body = "{\"schemas\":[\"" + USER_SCHEMA + "\"],\"userName\":\"dup@corp.example\"}";
    mvc.perform(
            post("/scim/v2/Users")
                .header("Authorization", AUTH)
                .contentType(SCIM_JSON)
                .content(body))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/scim/v2/Users")
                .header("Authorization", AUTH)
                .contentType(SCIM_JSON)
                .content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.schemas[0]").value(ERROR_SCHEMA))
        .andExpect(jsonPath("$.scimType").value("uniqueness"));
  }

  @Test
  void missingUserIs404InScimShape() throws Exception {
    mvc.perform(get("/scim/v2/Users/" + UUID.randomUUID()).header("Authorization", AUTH))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.schemas[0]").value(ERROR_SCHEMA))
        .andExpect(jsonPath("$.status").value("404"));
  }
}

package com.auvex.gateway;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.auvex.gateway.member.Member;
import com.auvex.gateway.member.MemberRepository;
import com.auvex.gateway.support.AbstractPostgresIntegrationTest;
import com.auvex.gateway.tenant.Tenant;
import com.auvex.gateway.tenant.TenantRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Exercises the console auth scaffold: dev login mints a session that /session resolves. */
@AutoConfigureMockMvc
class AuthScaffoldIntegrationTest extends AbstractPostgresIntegrationTest {

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("auvex.auth.dev-login-enabled", () -> true);
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantRepository tenants;
  @Autowired private MemberRepository members;

  @Test
  void devLoginIssuesASessionThatResolves() throws Exception {
    String slug = "acme-" + UUID.randomUUID();
    Tenant tenant = tenants.save(new Tenant("Acme", slug));
    members.save(new Member(tenant.getId(), "maya@acme.com", "Maya", "owner", "active"));

    String login =
        mvc.perform(
                post("/auth/dev-login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"tenantSlug\":\"" + slug + "\",\"email\":\"maya@acme.com\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("owner"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String token = JsonPath.read(login, "$.token");

    mvc.perform(get("/auth/session").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("maya@acme.com"))
        .andExpect(jsonPath("$.role").value("owner"));

    mvc.perform(get("/auth/session").header("Authorization", "Bearer not.a.valid.token"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void providersReportTheirConfigurationStatus() throws Exception {
    mvc.perform(get("/auth/providers"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name=='google')].configured", contains(false)));
  }
}

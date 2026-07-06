package com.gatewise.gateway;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gatewise.gateway.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Proves the demo seed (when enabled) creates a usable sandbox tenant with sample data. */
@AutoConfigureMockMvc
class DemoSeederIntegrationTest extends AbstractPostgresIntegrationTest {

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("gatewise.demo.enabled", () -> true);
  }

  @Autowired private MockMvc mvc;

  @Test
  void demoKeyResolvesToASeededTenant() throws Exception {
    mvc.perform(get("/v1/me").header("Authorization", "Bearer gatewise_demo_key"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Demo Org"));

    mvc.perform(get("/v1/policies").header("Authorization", "Bearer gatewise_demo_key"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3));

    mvc.perform(get("/v1/members").header("Authorization", "Bearer gatewise_demo_key"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3));
  }
}

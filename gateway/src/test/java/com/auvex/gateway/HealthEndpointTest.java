package com.auvex.gateway;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Proves the gateway comes up as a real web service and reports its health.
 *
 * <p>The docker-compose stack and, later, the AWS load balancer both decide whether the gateway is
 * alive by calling /actuator/health. This test pins that contract so it can't silently break: the
 * endpoint must exist and report UP once the context is ready.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HealthEndpointTest {

  @Autowired private MockMvc mockMvc;

  /** A ready gateway answers its health probe with 200 and status "UP". */
  @Test
  void healthEndpointReportsUp() throws Exception {
    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }
}

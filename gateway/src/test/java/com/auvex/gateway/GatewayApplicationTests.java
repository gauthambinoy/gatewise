package com.auvex.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test for the gateway.
 *
 * <p>If the Spring context can't wire itself up — a bad bean, a broken auto-configuration, a
 * missing dependency — this test fails before any feature test even runs. It's the cheapest early
 * warning that the application is fundamentally bootable.
 */
@SpringBootTest
class GatewayApplicationTests {

  /** The application context must load cleanly. */
  @Test
  void contextLoads() {
    // No assertions needed: a failure to build the context fails the test.
  }
}

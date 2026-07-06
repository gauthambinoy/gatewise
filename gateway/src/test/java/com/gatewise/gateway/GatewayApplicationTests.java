package com.gatewise.gateway;

import com.gatewise.gateway.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Smoke test for the gateway.
 *
 * <p>If the Spring context can't wire itself up — a bad bean, a broken auto-configuration, a failed
 * migration — this fails before any feature test runs. It boots against a real Postgres (via the
 * base class) so the datasource and Flyway are exercised too, not stubbed out.
 */
class GatewayApplicationTests extends AbstractPostgresIntegrationTest {

  /** The application context must load cleanly against a real database. */
  @Test
  void contextLoads() {
    // No assertions needed: a failure to build the context fails the test.
  }
}

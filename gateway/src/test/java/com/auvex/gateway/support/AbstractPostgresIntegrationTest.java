package com.auvex.gateway.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for tests that need the full Spring context plus a real database.
 *
 * <p>It boots one Postgres container shared by every integration test in the JVM — started once and
 * left to Testcontainers' reaper to stop at exit, which is far faster than a fresh container per
 * test class. Subclasses just extend this and get a live, migrated database wired into Spring.
 */
@SpringBootTest
public abstract class AbstractPostgresIntegrationTest {

  // Private + static: one container for the whole test run. Subclasses never
  // touch it directly, so keeping it private also avoids exposing a mutable
  // static field.
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:17-alpine");

  static {
    POSTGRES.start();
  }

  /** Point Spring's datasource at the container before the context starts. */
  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }
}

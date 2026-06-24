package com.auvex.gateway;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.auvex.gateway.ratelimit.QuotaExceededException;
import com.auvex.gateway.ratelimit.QuotaService;
import com.auvex.gateway.support.AbstractPostgresIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;

/** Proves the per-user daily quota rejects the call once the cap is reached. */
class QuotaIntegrationTest extends AbstractPostgresIntegrationTest {

  @SuppressWarnings("resource") // shared for the JVM; Testcontainers' reaper stops it at exit
  private static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  static {
    REDIS.start();
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    registry.add("auvex.quota.enabled", () -> true);
    registry.add("auvex.quota.per-user-per-day", () -> 1);
  }

  @Autowired private QuotaService quotas;

  @Test
  void rejectsTheSecondCallForTheSameUser() {
    UUID tenant = UUID.randomUUID();
    assertThatCode(() -> quotas.check(tenant, "alice", "openai/gpt-oss-120b:free"))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> quotas.check(tenant, "alice", "openai/gpt-oss-120b:free"))
        .isInstanceOf(QuotaExceededException.class)
        .hasMessageContaining("per-user daily quota");
  }

  @Test
  void countsUsersIndependently() {
    UUID tenant = UUID.randomUUID();
    quotas.check(tenant, "carol", "m");
    // A different caller has their own counter, so they're still under the cap.
    assertThatCode(() -> quotas.check(tenant, "dave", "m")).doesNotThrowAnyException();
  }
}

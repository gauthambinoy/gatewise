package com.gatewise.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.gatewise.gateway.audit.AuditEntry;
import com.gatewise.gateway.audit.AuditLogRepository;
import com.gatewise.gateway.audit.AuditSink;
import com.gatewise.gateway.audit.Verdict;
import com.gatewise.gateway.support.AbstractPostgresIntegrationTest;
import com.gatewise.gateway.tenant.Tenant;
import com.gatewise.gateway.tenant.TenantRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/** Proves the async audit pipeline: an entry published to Kafka is persisted by the consumer. */
class KafkaAuditIntegrationTest extends AbstractPostgresIntegrationTest {

  @SuppressWarnings("deprecation") // the classic KafkaContainer is the most portable here
  private static final KafkaContainer KAFKA =
      new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

  static {
    KAFKA.start();
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    registry.add("gatewise.audit.async", () -> true);
  }

  @Autowired private AuditSink sink;
  @Autowired private AuditLogRepository auditLog;
  @Autowired private TenantRepository tenants;

  @Test
  void aPublishedEntryIsPersistedAsynchronously() {
    UUID tenantId = tenants.save(new Tenant("Acme", "acme-" + UUID.randomUUID())).getId();

    sink.record(
        new AuditEntry(
            tenantId,
            UUID.randomUUID(),
            "svc",
            "model-x",
            Verdict.ALLOWED,
            "prompt",
            null,
            Instant.now()));

    // The consumer persists off the request thread, so wait for the row to land.
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertThat(auditLog.findByTenantIdOrderByIdAsc(tenantId)).hasSize(1));
  }
}

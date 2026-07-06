package com.gatewise.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.gatewise.gateway.audit.AuditEntry;
import com.gatewise.gateway.audit.AuditService;
import com.gatewise.gateway.audit.Verdict;
import com.gatewise.gateway.support.AbstractPostgresIntegrationTest;
import com.gatewise.gateway.tenant.Tenant;
import com.gatewise.gateway.tenant.TenantRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Proves a cost-anomaly alert is raised and posted to the webhook when spend crosses the threshold.
 */
class CostAnomalyIntegrationTest extends AbstractPostgresIntegrationTest {

  private static final MockWebServer SIEM = new MockWebServer();

  static {
    try {
      SIEM.start();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("gatewise.cost-alert.enabled", () -> true);
    registry.add("gatewise.cost-alert.threshold-usd", () -> "0.01");
    registry.add("gatewise.webhook.url", () -> "http://localhost:" + SIEM.getPort() + "/alerts");
  }

  @Autowired private AuditService audit;
  @Autowired private TenantRepository tenants;

  @Test
  void alertsWhenSpendCrossesTheThreshold() throws Exception {
    SIEM.enqueue(new MockResponse().setResponseCode(200));
    UUID tenantId = tenants.save(new Tenant("Acme", "acme-" + UUID.randomUUID())).getId();

    audit.append(
        new AuditEntry(
            tenantId,
            UUID.randomUUID(),
            "svc",
            "model-x",
            Verdict.ALLOWED,
            "prompt",
            null,
            Instant.now(),
            10,
            20,
            new BigDecimal("1.00"),
            Map.of()));

    await().atMost(Duration.ofSeconds(10)).until(() -> SIEM.getRequestCount() >= 1);
    RecordedRequest sent = SIEM.takeRequest();
    assertThat(sent.getPath()).isEqualTo("/alerts");
    assertThat(sent.getBody().readUtf8()).contains("cost_anomaly");
  }
}

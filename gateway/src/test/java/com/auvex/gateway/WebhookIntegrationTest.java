package com.auvex.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.auvex.gateway.audit.AuditEntry;
import com.auvex.gateway.audit.AuditService;
import com.auvex.gateway.audit.Verdict;
import com.auvex.gateway.support.AbstractPostgresIntegrationTest;
import com.auvex.gateway.tenant.Tenant;
import com.auvex.gateway.tenant.TenantRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Proves an audit event is streamed to the configured webhook. */
class WebhookIntegrationTest extends AbstractPostgresIntegrationTest {

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
    registry.add("auvex.webhook.enabled", () -> true);
    registry.add("auvex.webhook.url", () -> "http://localhost:" + SIEM.getPort() + "/events");
  }

  @Autowired private AuditService audit;
  @Autowired private TenantRepository tenants;

  @Test
  void forwardsAuditEventsToTheWebhook() throws Exception {
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
            Instant.now()));

    // Delivery is async, so wait for the webhook to receive the event.
    await().atMost(Duration.ofSeconds(10)).until(() -> SIEM.getRequestCount() >= 1);
    RecordedRequest sent = SIEM.takeRequest();
    assertThat(sent.getPath()).isEqualTo("/events");
    assertThat(sent.getBody().readUtf8()).contains("\"verdict\":\"allowed\"");
  }
}

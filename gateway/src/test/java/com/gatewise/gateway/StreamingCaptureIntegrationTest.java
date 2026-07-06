package com.gatewise.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gatewise.gateway.audit.AuditLog;
import com.gatewise.gateway.audit.AuditLogRepository;
import com.gatewise.gateway.auth.ApiKeyHasher;
import com.gatewise.gateway.support.AbstractPostgresIntegrationTest;
import com.gatewise.gateway.tenant.ApiKey;
import com.gatewise.gateway.tenant.ApiKeyRepository;
import com.gatewise.gateway.tenant.Tenant;
import com.gatewise.gateway.tenant.TenantRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Proves a streamed response is teed to the caller and captured (audited) for PII after the fact.
 */
@AutoConfigureMockMvc
class StreamingCaptureIntegrationTest extends AbstractPostgresIntegrationTest {

  private static final MockWebServer UPSTREAM = new MockWebServer();

  static {
    try {
      UPSTREAM.start();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("gatewise.openrouter.base-url", () -> "http://localhost:" + UPSTREAM.getPort());
    registry.add("gatewise.openrouter.api-key", () -> "test-upstream-key");
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantRepository tenants;
  @Autowired private ApiKeyRepository apiKeys;
  @Autowired private AuditLogRepository auditLog;

  @Test
  void teesTheStreamAndAuditsTheCapturedResponse() throws Exception {
    Tenant tenant = tenants.save(new Tenant("Acme", "acme-" + UUID.randomUUID()));
    String raw = "gatewise_" + UUID.randomUUID().toString().replace("-", "");
    apiKeys.save(
        new ApiKey(tenant.getId(), "default", ApiKeyHasher.hash(raw), raw.substring(0, 12)));

    // An SSE stream whose assembled content leaks an email.
    String sse =
        "data: {\"choices\":[{\"delta\":{\"content\":\"Reach \"}}]}\n\n"
            + "data: {\"choices\":[{\"delta\":{\"content\":\"bob@secret.com\"}}]}\n\n"
            + "data: [DONE]\n\n";
    UPSTREAM.enqueue(
        new MockResponse().setHeader("Content-Type", "text/event-stream").setBody(sse));

    String body =
        "{\"model\":\"smart\",\"stream\":true,"
            + "\"messages\":[{\"role\":\"user\",\"content\":\"who do I contact?\"}]}";
    String streamed =
        mvc.perform(
                post("/v1/chat/completions")
                    .header("Authorization", "Bearer " + raw)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // The caller still received the live stream (response redaction can't unsend a stream).
    assertThat(streamed).contains("bob@secret.com");

    // ...but the gateway captured it: the audit records the streamed response as redacted.
    List<AuditLog> rows = auditLog.findByTenantIdOrderByIdAsc(tenant.getId());
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).getVerdict()).isEqualTo("redacted");
    assertThat(rows.get(0).getRedactionCounts()).containsEntry("email", 1);
  }
}

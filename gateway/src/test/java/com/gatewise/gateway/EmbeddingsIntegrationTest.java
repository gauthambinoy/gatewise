package com.gatewise.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Proves the embeddings endpoint redacts the input before forwarding and records the call. */
@AutoConfigureMockMvc
class EmbeddingsIntegrationTest extends AbstractPostgresIntegrationTest {

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
  void redactsTheInputThenForwardsAndAudits() throws Exception {
    Tenant tenant = tenants.save(new Tenant("Acme", "acme-" + UUID.randomUUID()));
    String raw = "gatewise_" + UUID.randomUUID().toString().replace("-", "");
    apiKeys.save(
        new ApiKey(tenant.getId(), "default", ApiKeyHasher.hash(raw), raw.substring(0, 12)));

    UPSTREAM.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("{\"object\":\"list\",\"data\":[{\"index\":0,\"embedding\":[0.1,0.2]}]}"));

    String body = "{\"model\":\"text-embedding-3-small\",\"input\":\"email me at bob@secret.com\"}";
    mvc.perform(
            post("/v1/embeddings")
                .header("Authorization", "Bearer " + raw)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].embedding").isArray());

    // The provider received the input with the email masked out.
    RecordedRequest sent = UPSTREAM.takeRequest();
    assertThat(sent.getPath()).isEqualTo("/embeddings");
    assertThat(sent.getBody().readUtf8()).doesNotContain("bob@secret.com");

    // The call was recorded as a redacted embedding request.
    List<AuditLog> rows = auditLog.findByTenantIdOrderByIdAsc(tenant.getId());
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).getVerdict()).isEqualTo("redacted");
    assertThat(rows.get(0).getModel()).isEqualTo("text-embedding-3-small");
  }
}

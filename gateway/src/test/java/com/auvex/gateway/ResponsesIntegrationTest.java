package com.auvex.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.auvex.gateway.audit.AuditLog;
import com.auvex.gateway.audit.AuditLogRepository;
import com.auvex.gateway.auth.ApiKeyHasher;
import com.auvex.gateway.support.AbstractPostgresIntegrationTest;
import com.auvex.gateway.tenant.ApiKey;
import com.auvex.gateway.tenant.ApiKeyRepository;
import com.auvex.gateway.tenant.Tenant;
import com.auvex.gateway.tenant.TenantRepository;
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

/**
 * Proves the Responses API endpoint redacts the {@code input} (string and array shapes) before
 * forwarding, records the call, and screens the returned text into the audit log.
 */
@AutoConfigureMockMvc
class ResponsesIntegrationTest extends AbstractPostgresIntegrationTest {

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
    registry.add("auvex.openrouter.base-url", () -> "http://localhost:" + UPSTREAM.getPort());
    registry.add("auvex.openrouter.api-key", () -> "test-upstream-key");
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantRepository tenants;
  @Autowired private ApiKeyRepository apiKeys;
  @Autowired private AuditLogRepository auditLog;

  private String authKey(UUID... out) {
    Tenant tenant = tenants.save(new Tenant("Acme", "acme-" + UUID.randomUUID()));
    String raw = "auvex_" + UUID.randomUUID().toString().replace("-", "");
    apiKeys.save(
        new ApiKey(tenant.getId(), "default", ApiKeyHasher.hash(raw), raw.substring(0, 12)));
    if (out.length > 0) {
      out[0] = tenant.getId();
    }
    return raw;
  }

  @Test
  void redactsStringInputThenForwardsAndScreensResponse() throws Exception {
    UUID[] tenantId = new UUID[1];
    String key = authKey(tenantId);

    // The provider echoes back a credit-card number, which must be masked in the audit log.
    UPSTREAM.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(
                "{\"output\":[{\"content\":[{\"type\":\"output_text\","
                    + "\"text\":\"Your card 4111 1111 1111 1111 is on file.\"}]}]}"));

    String body =
        "{\"model\":\"gpt-4o\",\"input\":\"My email is bob@secret.com, please remember it.\"}";
    mvc.perform(
            post("/v1/responses")
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());

    RecordedRequest sent = UPSTREAM.takeRequest();
    assertThat(sent.getPath()).isEqualTo("/responses");
    assertThat(sent.getBody().readUtf8()).doesNotContain("bob@secret.com");

    List<AuditLog> rows = auditLog.findByTenantIdOrderByIdAsc(tenantId[0]);
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).getVerdict()).isEqualTo("redacted");
    assertThat(rows.get(0).getModel()).isEqualTo("gpt-4o");
    // The response text was screened: the card number is masked, not stored raw.
    assertThat(rows.get(0).getResponseRedacted()).doesNotContain("4111 1111 1111 1111");
    assertThat(rows.get(0).getResponseRedacted()).isNotBlank();
  }

  @Test
  void redactsArrayInputParts() throws Exception {
    UUID[] tenantId = new UUID[1];
    String key = authKey(tenantId);
    UPSTREAM.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("{\"output\":[]}"));

    String body =
        "{\"model\":\"gpt-4o\",\"input\":[{\"role\":\"user\",\"content\":"
            + "[{\"type\":\"input_text\",\"text\":\"reach me at carol@private.com\"}]}]}";
    mvc.perform(
            post("/v1/responses")
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());

    RecordedRequest sent = UPSTREAM.takeRequest();
    assertThat(sent.getBody().readUtf8()).doesNotContain("carol@private.com");
    assertThat(auditLog.findByTenantIdOrderByIdAsc(tenantId[0]).get(0).getVerdict())
        .isEqualTo("redacted");
  }

  @Test
  void rejectsMissingInput() throws Exception {
    String key = authKey();
    mvc.perform(
            post("/v1/responses")
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"model\":\"gpt-4o\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.type").value("invalid_request_error"));
  }
}

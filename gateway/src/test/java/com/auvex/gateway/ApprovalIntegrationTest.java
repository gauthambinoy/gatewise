package com.auvex.gateway;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.auvex.gateway.auth.ApiKeyHasher;
import com.auvex.gateway.support.AbstractPostgresIntegrationTest;
import com.auvex.gateway.tenant.ApiKey;
import com.auvex.gateway.tenant.ApiKeyRepository;
import com.auvex.gateway.tenant.Tenant;
import com.auvex.gateway.tenant.TenantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
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
import org.springframework.test.web.servlet.MvcResult;

/** Proves the human-in-the-loop flow: a high-risk call is held, approved, then proceeds. */
@AutoConfigureMockMvc
class ApprovalIntegrationTest extends AbstractPostgresIntegrationTest {

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
    registry.add("auvex.approval.enabled", () -> true);
    registry.add("auvex.approval.review-data-types", () -> "credit_card");
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantRepository tenants;
  @Autowired private ApiKeyRepository apiKeys;
  @Autowired private ObjectMapper objectMapper;

  private static final String CARD_BODY =
      "{\"model\":\"smart\",\"messages\":[{\"role\":\"user\","
          + "\"content\":\"charge card 4012888888881881\"}]}";

  private String authKey() {
    Tenant t = tenants.save(new Tenant("Acme", "acme-" + UUID.randomUUID()));
    String raw = "auvex_" + UUID.randomUUID().toString().replace("-", "");
    apiKeys.save(new ApiKey(t.getId(), "default", ApiKeyHasher.hash(raw), raw.substring(0, 12)));
    return raw;
  }

  @Test
  void highRiskCallIsHeldApprovedThenProceeds() throws Exception {
    String key = authKey();

    // 1. The card triggers a review-data-type → held, not forwarded.
    MvcResult held =
        mvc.perform(
                post("/v1/chat/completions")
                    .header("Authorization", "Bearer " + key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(CARD_BODY))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("pending_approval"))
            .andReturn();
    String approvalId =
        objectMapper.readTree(held.getResponse().getContentAsString()).get("approval_id").asText();

    // 2. It shows up in the reviewer's queue with the reason.
    mvc.perform(get("/v1/approvals").header("Authorization", "Bearer " + key))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].reason").value(containsString("credit_card")));

    // 3. Approve it.
    mvc.perform(
            post("/v1/approvals/" + approvalId + "/decision")
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"approve\",\"decidedBy\":\"reviewer@acme.com\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("approved"));

    // 4. The same prompt now passes straight through to the provider.
    UPSTREAM.enqueue(
        new MockResponse().setHeader("Content-Type", "application/json").setBody("{\"ok\":true}"));
    mvc.perform(
            post("/v1/chat/completions")
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CARD_BODY))
        .andExpect(status().isOk());
  }

  @Test
  void cleanCallIsNotHeld() throws Exception {
    String key = authKey();
    UPSTREAM.enqueue(
        new MockResponse().setHeader("Content-Type", "application/json").setBody("{\"ok\":true}"));
    // No sensitive data → no review trigger → forwarded normally.
    mvc.perform(
            post("/v1/chat/completions")
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"model\":\"smart\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
        .andExpect(status().isOk());
  }
}

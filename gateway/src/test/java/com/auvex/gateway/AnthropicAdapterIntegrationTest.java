package com.auvex.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.auvex.gateway.auth.ApiKeyHasher;
import com.auvex.gateway.support.AbstractPostgresIntegrationTest;
import com.auvex.gateway.tenant.ApiKey;
import com.auvex.gateway.tenant.ApiKeyRepository;
import com.auvex.gateway.tenant.Tenant;
import com.auvex.gateway.tenant.TenantRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
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

/** Proves an anthropic/* model is sent natively to /v1/messages and translated both ways. */
@AutoConfigureMockMvc
class AnthropicAdapterIntegrationTest extends AbstractPostgresIntegrationTest {

  private static final MockWebServer ANTHROPIC = new MockWebServer();

  static {
    try {
      ANTHROPIC.start();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("auvex.anthropic.enabled", () -> true);
    registry.add("auvex.anthropic.base-url", () -> "http://localhost:" + ANTHROPIC.getPort());
    registry.add("auvex.anthropic.api-key", () -> "test-anthropic-key");
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantRepository tenants;
  @Autowired private ApiKeyRepository apiKeys;

  @Test
  void routesAnthropicModelNativelyAndTranslatesBothWays() throws Exception {
    Tenant tenant = tenants.save(new Tenant("Acme", "acme-" + UUID.randomUUID()));
    String raw = "auvex_" + UUID.randomUUID().toString().replace("-", "");
    apiKeys.save(
        new ApiKey(tenant.getId(), "default", ApiKeyHasher.hash(raw), raw.substring(0, 12)));

    ANTHROPIC.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(
                "{\"id\":\"msg_1\",\"model\":\"claude-3-5-sonnet-20241022\",\"content\":["
                    + "{\"type\":\"text\",\"text\":\"Hi from Claude\"}],\"stop_reason\":\"end_turn\","
                    + "\"usage\":{\"input_tokens\":7,\"output_tokens\":4}}"));

    // The client uses the "claude" alias, which the router resolves to anthropic/claude-…
    String body = "{\"model\":\"claude\",\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}]}";
    mvc.perform(
            post("/v1/chat/completions")
                .header("Authorization", "Bearer " + raw)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        // The caller gets an OpenAI-shaped response, translated from Anthropic's.
        .andExpect(jsonPath("$.choices[0].message.content").value("Hi from Claude"))
        .andExpect(jsonPath("$.usage.prompt_tokens").value(7))
        .andExpect(jsonPath("$.usage.completion_tokens").value(4));

    // The gateway actually called Anthropic's native endpoint with a translated request.
    RecordedRequest sent = ANTHROPIC.takeRequest();
    assertThat(sent.getPath()).isEqualTo("/v1/messages");
    String sentBody = sent.getBody().readUtf8();
    assertThat(sentBody).contains("\"max_tokens\"").contains("claude-3-5-sonnet-20241022");
    assertThat(sentBody).doesNotContain("anthropic/"); // the provider prefix was stripped
  }
}

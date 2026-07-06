package com.gatewise.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gatewise.gateway.auth.ApiKeyHasher;
import com.gatewise.gateway.support.AbstractPostgresIntegrationTest;
import com.gatewise.gateway.tenant.ApiKey;
import com.gatewise.gateway.tenant.ApiKeyRepository;
import com.gatewise.gateway.tenant.Tenant;
import com.gatewise.gateway.tenant.TenantRepository;
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

/** Proves a google/* model is sent natively to generateContent and translated both ways. */
@AutoConfigureMockMvc
class GeminiAdapterIntegrationTest extends AbstractPostgresIntegrationTest {

  private static final MockWebServer GEMINI = new MockWebServer();

  static {
    try {
      GEMINI.start();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("gatewise.gemini.enabled", () -> true);
    registry.add("gatewise.gemini.base-url", () -> "http://localhost:" + GEMINI.getPort());
    registry.add("gatewise.gemini.api-key", () -> "test-gemini-key");
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantRepository tenants;
  @Autowired private ApiKeyRepository apiKeys;

  @Test
  void routesGeminiModelNativelyAndTranslatesBothWays() throws Exception {
    Tenant tenant = tenants.save(new Tenant("Acme", "acme-" + UUID.randomUUID()));
    String raw = "gatewise_" + UUID.randomUUID().toString().replace("-", "");
    apiKeys.save(
        new ApiKey(tenant.getId(), "default", ApiKeyHasher.hash(raw), raw.substring(0, 12)));

    GEMINI.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(
                "{\"modelVersion\":\"gemini-1.5-pro\",\"candidates\":[{\"content\":{\"parts\":["
                    + "{\"text\":\"Hi from Gemini\"}]},\"finishReason\":\"STOP\"}],"
                    + "\"usageMetadata\":{\"promptTokenCount\":7,\"candidatesTokenCount\":4,"
                    + "\"totalTokenCount\":11}}"));

    // The client uses the "gemini" alias, which the router resolves to google/gemini-1.5-pro.
    String body = "{\"model\":\"gemini\",\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}]}";
    mvc.perform(
            post("/v1/chat/completions")
                .header("Authorization", "Bearer " + raw)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        // The caller gets an OpenAI-shaped response, translated from Gemini's.
        .andExpect(jsonPath("$.choices[0].message.content").value("Hi from Gemini"))
        .andExpect(jsonPath("$.usage.prompt_tokens").value(7))
        .andExpect(jsonPath("$.usage.completion_tokens").value(4));

    // The gateway actually called Gemini's native endpoint with the prefix-stripped model.
    RecordedRequest sent = GEMINI.takeRequest();
    assertThat(sent.getPath()).startsWith("/v1beta/models/gemini-1.5-pro:generateContent");
    String sentBody = sent.getBody().readUtf8();
    assertThat(sentBody).contains("\"contents\"").contains("\"generationConfig\"");
    assertThat(sentBody).doesNotContain("google/"); // the provider prefix went in the URL, not body
  }
}

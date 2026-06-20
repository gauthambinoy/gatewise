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
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Exercises the proxy hot path end to end: a real authenticated request in, a real (mock) upstream
 * provider out. Covers the happy path, validation, upstream failure, rate-limit passthrough and
 * streaming.
 */
@AutoConfigureMockMvc
class ChatCompletionsProxyTest extends AbstractPostgresIntegrationTest {

  private static final MockWebServer UPSTREAM = new MockWebServer();

  static {
    try {
      UPSTREAM.start();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @DynamicPropertySource
  static void upstreamProperties(DynamicPropertyRegistry registry) {
    registry.add("auvex.openrouter.base-url", () -> "http://localhost:" + UPSTREAM.getPort());
    registry.add("auvex.openrouter.api-key", () -> "test-upstream-key");
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantRepository tenants;
  @Autowired private ApiKeyRepository apiKeys;

  // "smart" is a configured alias that routes to the provider model openai/gpt-4o.
  private static final String VALID_BODY =
      "{\"model\":\"smart\",\"messages\":[{\"role\":\"user\",\"content\":\"hey\"}]}";

  // Creates a tenant + active key and returns the raw key to authenticate with.
  private String authKey() {
    Tenant t = tenants.save(new Tenant("Acme", "acme-" + UUID.randomUUID()));
    String raw = "auvex_" + UUID.randomUUID().toString().replace("-", "");
    apiKeys.save(new ApiKey(t.getId(), "default", ApiKeyHasher.hash(raw), raw.substring(0, 12)));
    return raw;
  }

  // The MockWebServer is shared across tests; drop any request recorded by an earlier
  // test so each test inspects only its own (a disconnected request has a null path).
  @BeforeEach
  void drainRecordedRequests() throws InterruptedException {
    while (UPSTREAM.takeRequest(1, TimeUnit.MILLISECONDS) != null) {
      // discard
    }
  }

  @Test
  void forwardsRequestAndReturnsUpstreamResponse() throws Exception { // T12
    UPSTREAM.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(
                "{\"id\":\"chatcmpl-1\",\"choices\":[{\"message\":{\"content\":\"hi there\"}}]}"));
    String key = authKey();

    mvc.perform(
            post("/v1/chat/completions")
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.choices[0].message.content").value("hi there"));

    RecordedRequest sent = UPSTREAM.takeRequest();
    assertThat(sent.getPath()).isEqualTo("/chat/completions");
    assertThat(sent.getHeader("Authorization")).isEqualTo("Bearer test-upstream-key");
    assertThat(sent.getBody().readUtf8()).contains("openai/gpt-4o");
  }

  @Test
  void malformedRequestIsBadRequest() throws Exception { // T13
    String key = authKey();
    // 'messages' missing
    mvc.perform(
            post("/v1/chat/completions")
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"model\":\"openai/gpt-4o\"}"))
        .andExpect(status().isBadRequest());
    // not JSON at all
    mvc.perform(
            post("/v1/chat/completions")
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("not json"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void upstreamFailureIsGatewayTimeout() throws Exception { // T14
    UPSTREAM.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
    String key = authKey();

    mvc.perform(
            post("/v1/chat/completions")
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY))
        .andExpect(status().isGatewayTimeout());
  }

  @Test
  void providerRateLimitIsPassedThrough() throws Exception {
    UPSTREAM.enqueue(
        new MockResponse()
            .setResponseCode(429)
            .setHeader("Content-Type", "application/json")
            .setBody("{\"error\":\"rate limited\"}"));
    String key = authKey();

    mvc.perform(
            post("/v1/chat/completions")
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY))
        .andExpect(status().isTooManyRequests());
  }

  @Test
  void aliasIsRewrittenToProviderModel() throws Exception { // T16
    UPSTREAM.enqueue(
        new MockResponse().setHeader("Content-Type", "application/json").setBody("{\"ok\":true}"));
    String key = authKey();
    String body = "{\"model\":\"fast\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}";

    mvc.perform(
            post("/v1/chat/completions")
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());

    // The alias "fast" must reach the provider as openai/gpt-4o-mini, not as "fast".
    String forwarded = UPSTREAM.takeRequest().getBody().readUtf8();
    assertThat(forwarded).contains("openai/gpt-4o-mini").doesNotContain("\"fast\"");
  }

  @Test
  void redactsSensitiveDataBeforeForwarding() throws Exception { // redaction wired into the proxy
    UPSTREAM.enqueue(
        new MockResponse().setHeader("Content-Type", "application/json").setBody("{\"ok\":true}"));
    String key = authKey();
    String body =
        "{\"model\":\"smart\",\"messages\":[{\"role\":\"user\","
            + "\"content\":\"my card is 4012888888881881 and email a@b.io\"}]}";

    mvc.perform(
            post("/v1/chat/completions")
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());

    // What reaches the provider must be masked, never the raw PII.
    String forwarded = UPSTREAM.takeRequest().getBody().readUtf8();
    assertThat(forwarded)
        .doesNotContain("4012888888881881")
        .doesNotContain("a@b.io")
        .contains("CARD_REDACTED");
  }

  @Test
  void unknownModelIsRejected() throws Exception { // T17
    String key = authKey();
    String body =
        "{\"model\":\"no-such-model\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}";

    mvc.perform(
            post("/v1/chat/completions")
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void streamingResponseIsRelayed() throws Exception { // T15
    String sse =
        "data: {\"choices\":[{\"delta\":{\"content\":\"He\"}}]}\n\n"
            + "data: {\"choices\":[{\"delta\":{\"content\":\"llo\"}}]}\n\n"
            + "data: [DONE]\n\n";
    UPSTREAM.enqueue(
        new MockResponse().setHeader("Content-Type", "text/event-stream").setBody(sse));
    String key = authKey();
    String streamingBody =
        "{\"model\":\"smart\","
            + "\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],"
            + "\"stream\":true}";

    MvcResult result =
        mvc.perform(
                post("/v1/chat/completions")
                    .header("Authorization", "Bearer " + key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(streamingBody))
            .andExpect(status().isOk())
            .andReturn();

    assertThat(result.getResponse().getContentType()).contains("text/event-stream");
    String body = result.getResponse().getContentAsString();
    assertThat(body).contains("\"He\"").contains("\"llo\"").contains("[DONE]");
  }
}

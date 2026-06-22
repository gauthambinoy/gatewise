package com.auvex.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.auvex.gateway.auth.ApiKeyHasher;
import com.auvex.gateway.support.AbstractPostgresIntegrationTest;
import com.auvex.gateway.tenant.ApiKey;
import com.auvex.gateway.tenant.ApiKeyRepository;
import com.auvex.gateway.tenant.Tenant;
import com.auvex.gateway.tenant.TenantRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
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
 * Proves reversible tokenization: the provider sees a token, and the caller gets their own value
 * restored in the response.
 */
@AutoConfigureMockMvc
class ReversibleTokenizationIntegrationTest extends AbstractPostgresIntegrationTest {

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
    registry.add("auvex.tokenization.enabled", () -> true);
    registry.add("auvex.cache.enabled", () -> false);
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantRepository tenants;
  @Autowired private ApiKeyRepository apiKeys;

  private static String tokenFor(String value) throws Exception {
    byte[] digest =
        MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    return "⟦EMAIL_" + HexFormat.of().formatHex(digest, 0, 4) + "⟧";
  }

  @Test
  void tokenizesForTheProviderAndRestoresForTheCaller() throws Exception {
    Tenant tenant = tenants.save(new Tenant("Acme", "acme-" + UUID.randomUUID()));
    String raw = "auvex_" + UUID.randomUUID().toString().replace("-", "");
    apiKeys.save(
        new ApiKey(tenant.getId(), "default", ApiKeyHasher.hash(raw), raw.substring(0, 12)));

    String email = "bob@secret.com";
    String token = tokenFor(email);
    // The model echoes the token it was given.
    UPSTREAM.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":"
                    + "\"I'll reach out to "
                    + token
                    + " shortly.\"}}]}"));

    String body =
        "{\"model\":\"smart\",\"messages\":[{\"role\":\"user\","
            + "\"content\":\"please email "
            + email
            + "\"}]}";
    String responseBody =
        mvc.perform(
                post("/v1/chat/completions")
                    .header("Authorization", "Bearer " + raw)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // The provider received the token, never the real email.
    RecordedRequest sent = UPSTREAM.takeRequest();
    String forwarded = sent.getBody().readUtf8();
    assertThat(forwarded).contains(token).doesNotContain(email);

    // The caller got their own value back, with the token gone.
    assertThat(responseBody).contains(email).doesNotContain(token);
  }
}

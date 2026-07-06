package com.gatewise.gateway;

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
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Proves a non-streaming request fails over to the fallback provider when the primary is down. */
@AutoConfigureMockMvc
class FailoverIntegrationTest extends AbstractPostgresIntegrationTest {

  private static final MockWebServer PRIMARY = new MockWebServer();
  private static final MockWebServer FALLBACK = new MockWebServer();

  static {
    try {
      PRIMARY.start();
      FALLBACK.start();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("gatewise.openrouter.base-url", () -> "http://localhost:" + PRIMARY.getPort());
    registry.add("gatewise.openrouter.api-key", () -> "primary-key");
    registry.add("gatewise.failover.enabled", () -> true);
    registry.add("gatewise.failover.base-url", () -> "http://localhost:" + FALLBACK.getPort());
    registry.add("gatewise.failover.api-key", () -> "fallback-key");
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantRepository tenants;
  @Autowired private ApiKeyRepository apiKeys;

  private String authKey() {
    Tenant t = tenants.save(new Tenant("Acme", "acme-" + UUID.randomUUID()));
    String raw = "gatewise_" + UUID.randomUUID().toString().replace("-", "");
    apiKeys.save(new ApiKey(t.getId(), "default", ApiKeyHasher.hash(raw), raw.substring(0, 12)));
    return raw;
  }

  @Test
  void failsOverToTheFallbackWhenThePrimaryIsDown() throws Exception {
    PRIMARY.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
    FALLBACK.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("{\"id\":\"from-fallback\"}"));
    String key = authKey();
    String body = "{\"model\":\"smart\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}";

    mvc.perform(
            post("/v1/chat/completions")
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("from-fallback"));
  }
}

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;

/** Proves the response cache: a repeat identical request is served from Redis, not the provider. */
@AutoConfigureMockMvc
class CacheIntegrationTest extends AbstractPostgresIntegrationTest {

  @SuppressWarnings("resource") // shared for the JVM; Testcontainers' reaper stops it at exit
  private static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  private static final MockWebServer UPSTREAM = new MockWebServer();

  static {
    REDIS.start();
    try {
      UPSTREAM.start();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    registry.add("gatewise.cache.enabled", () -> true);
    registry.add("gatewise.openrouter.base-url", () -> "http://localhost:" + UPSTREAM.getPort());
    registry.add("gatewise.openrouter.api-key", () -> "test-upstream-key");
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
  void secondIdenticalRequestIsServedFromCache() throws Exception {
    // Only one upstream response is enqueued: if the cache works, the second call never asks for a
    // second one.
    UPSTREAM.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("{\"id\":\"chatcmpl-cache\",\"choices\":[]}"));
    String key = authKey();
    String body = "{\"model\":\"smart\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}";

    for (int i = 0; i < 2; i++) {
      mvc.perform(
              post("/v1/chat/completions")
                  .header("Authorization", "Bearer " + key)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value("chatcmpl-cache"));
    }

    assertThat(UPSTREAM.getRequestCount()).isEqualTo(1);
  }
}

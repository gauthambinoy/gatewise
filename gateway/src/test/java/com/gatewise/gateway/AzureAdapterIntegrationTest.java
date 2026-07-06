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

/** Proves an azure/* model is sent to the Azure deployment URL with the deployment in the body. */
@AutoConfigureMockMvc
class AzureAdapterIntegrationTest extends AbstractPostgresIntegrationTest {

  private static final MockWebServer AZURE = new MockWebServer();

  static {
    try {
      AZURE.start();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("gatewise.azure.enabled", () -> true);
    registry.add("gatewise.azure.endpoint", () -> "http://localhost:" + AZURE.getPort());
    registry.add("gatewise.azure.api-key", () -> "test-azure-key");
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantRepository tenants;
  @Autowired private ApiKeyRepository apiKeys;

  @Test
  void routesAzureModelToDeploymentUrlWithStrippedPrefix() throws Exception {
    Tenant tenant = tenants.save(new Tenant("Acme", "acme-" + UUID.randomUUID()));
    String raw = "gatewise_" + UUID.randomUUID().toString().replace("-", "");
    apiKeys.save(
        new ApiKey(tenant.getId(), "default", ApiKeyHasher.hash(raw), raw.substring(0, 12)));

    // Azure returns the OpenAI shape verbatim, so no translation is needed.
    AZURE.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(
                "{\"id\":\"chatcmpl_1\",\"object\":\"chat.completion\",\"model\":\"gpt-4o\","
                    + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\","
                    + "\"content\":\"Hi from Azure\"},\"finish_reason\":\"stop\"}]}"));

    // The client uses the "azure-gpt4" alias, which the router resolves to azure/gpt-4o.
    String body =
        "{\"model\":\"azure-gpt4\",\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}]}";
    mvc.perform(
            post("/v1/chat/completions")
                .header("Authorization", "Bearer " + raw)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        // The caller gets the OpenAI-shaped response straight back.
        .andExpect(jsonPath("$.choices[0].message.content").value("Hi from Azure"));

    // The gateway called the Azure deployment endpoint with the deployment in the path…
    RecordedRequest sent = AZURE.takeRequest();
    assertThat(sent.getPath()).startsWith("/openai/deployments/gpt-4o/chat/completions");
    assertThat(sent.getPath()).contains("api-version=");
    // …and rewrote the body's model to the bare deployment name (no "azure/" prefix).
    String sentBody = sent.getBody().readUtf8();
    assertThat(sentBody).contains("\"model\":\"gpt-4o\"");
    assertThat(sentBody).doesNotContain("azure/");
  }
}

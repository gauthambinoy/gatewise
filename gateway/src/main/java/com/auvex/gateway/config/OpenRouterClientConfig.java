package com.auvex.gateway.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Builds the HTTP client used to talk to the upstream model provider. */
@Configuration
public class OpenRouterClientConfig {

  // A slow connect almost always means the provider is unreachable; a single
  // response can legitimately take a while (long generations), so the read budget
  // is generous. Both feed the 504 path when they're exceeded.
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

  /**
   * The primary provider client, pinned to its base URL with the gateway's API key baked in. Uses
   * the JDK HttpClient, which streams response bodies — exactly what relaying an SSE stream needs.
   */
  @Bean
  public RestClient openRouterRestClient(OpenRouterProperties props) {
    return client(props.baseUrl(), props.apiKey());
  }

  /**
   * The fallback provider client, used only when failover is enabled. When it isn't configured it
   * harmlessly falls back to the primary's URL/key so the bean is always valid.
   */
  @Bean
  public RestClient failoverRestClient(FailoverProperties failover, OpenRouterProperties primary) {
    String baseUrl = isBlank(failover.baseUrl()) ? primary.baseUrl() : failover.baseUrl();
    String apiKey = isBlank(failover.apiKey()) ? primary.apiKey() : failover.apiKey();
    return client(baseUrl, apiKey);
  }

  private static RestClient client(String baseUrl, String apiKey) {
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(READ_TIMEOUT);
    return RestClient.builder()
        .baseUrl(baseUrl)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
        .requestFactory(factory)
        .build();
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}

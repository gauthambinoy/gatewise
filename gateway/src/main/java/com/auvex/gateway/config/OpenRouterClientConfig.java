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
   * A RestClient pinned to the provider's base URL with the gateway's own API key baked in as a
   * default Authorization header. Uses the JDK HttpClient, which streams response bodies — exactly
   * what relaying a server-sent-events stream needs.
   */
  @Bean
  public RestClient openRouterRestClient(OpenRouterProperties props) {
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(READ_TIMEOUT);
    return RestClient.builder()
        .baseUrl(props.baseUrl())
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.apiKey())
        .requestFactory(factory)
        .build();
  }
}

package com.gatewise.gateway.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** The HTTP client for Anthropic's native API ({@code x-api-key} + {@code anthropic-version}). */
@Configuration
public class AnthropicClientConfig {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

  @Bean
  public RestClient anthropicRestClient(AnthropicProperties props) {
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(READ_TIMEOUT);
    return RestClient.builder()
        .baseUrl(props.baseUrl())
        .defaultHeader("x-api-key", props.apiKey() == null ? "" : props.apiKey())
        .defaultHeader("anthropic-version", props.version())
        .requestFactory(factory)
        .build();
  }
}

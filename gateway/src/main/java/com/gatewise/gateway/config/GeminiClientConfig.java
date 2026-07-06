package com.gatewise.gateway.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** The HTTP client for Gemini's native API ({@code x-goog-api-key}). */
@Configuration
public class GeminiClientConfig {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

  @Bean
  public RestClient geminiRestClient(GeminiProperties props) {
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(READ_TIMEOUT);
    return RestClient.builder()
        .baseUrl(props.baseUrl())
        .defaultHeader("x-goog-api-key", props.apiKey() == null ? "" : props.apiKey())
        .requestFactory(factory)
        .build();
  }
}

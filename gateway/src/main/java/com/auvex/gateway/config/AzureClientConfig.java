package com.auvex.gateway.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** The HTTP client for Azure OpenAI's API ({@code api-key} header, deployment in the path). */
@Configuration
public class AzureClientConfig {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

  @Bean
  public RestClient azureRestClient(AzureProperties props) {
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(READ_TIMEOUT);
    return RestClient.builder()
        .baseUrl(props.endpoint() == null ? "" : props.endpoint())
        .defaultHeader("api-key", props.apiKey() == null ? "" : props.apiKey())
        .requestFactory(factory)
        .build();
  }
}

package com.gatewise.gateway.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The HTTP client for Amazon Bedrock. Bedrock authenticates with AWS SigV4 (signed per request),
 * not a static header, so this provides a plain JDK {@link HttpClient} and the adapter attaches the
 * signed headers itself.
 */
@Configuration
public class BedrockClientConfig {

  @Bean
  public HttpClient bedrockHttpClient() {
    return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  }
}

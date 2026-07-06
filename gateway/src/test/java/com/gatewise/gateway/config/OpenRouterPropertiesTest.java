package com.gatewise.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Verifies the upstream-provider config binds correctly and, crucially, refuses to start when the
 * API key is missing — so a misconfiguration surfaces at boot, not on the first live call.
 */
class OpenRouterPropertiesTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

  @Test
  void bindsEveryFieldFromConfiguration() {
    runner
        .withPropertyValues(
            "gatewise.openrouter.base-url=https://openrouter.ai/api/v1",
            "gatewise.openrouter.model=openai/gpt-oss-120b:free",
            "gatewise.openrouter.api-key=sk-test-123")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              OpenRouterProperties props = context.getBean(OpenRouterProperties.class);
              assertThat(props.baseUrl()).isEqualTo("https://openrouter.ai/api/v1");
              assertThat(props.model()).isEqualTo("openai/gpt-oss-120b:free");
              assertThat(props.apiKey()).isEqualTo("sk-test-123");
            });
  }

  @Test
  void failsToStartWhenApiKeyIsMissing() {
    runner
        .withPropertyValues(
            "gatewise.openrouter.base-url=https://openrouter.ai/api/v1",
            "gatewise.openrouter.model=openai/gpt-oss-120b:free")
        // api-key intentionally omitted
        .run(
            context -> {
              assertThat(context).hasFailed();
              // Startup must fail on our config bean, specifically because the key is blank.
              assertThat(context.getStartupFailure())
                  .hasMessageContaining("OpenRouterProperties")
                  .hasStackTraceContaining("apiKey")
                  .hasStackTraceContaining("must not be blank");
            });
  }

  @Configuration
  @EnableConfigurationProperties(OpenRouterProperties.class)
  static class TestConfig {}
}

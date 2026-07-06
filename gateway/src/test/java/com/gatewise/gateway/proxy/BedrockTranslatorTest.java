package com.gatewise.gateway.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatewise.gateway.config.AnthropicProperties;
import com.gatewise.gateway.config.BedrockProperties;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Pure unit tests for the Bedrock body translation (reuses the Anthropic translator). */
class BedrockTranslatorTest {

  private final ObjectMapper json = new ObjectMapper();
  private final BedrockTranslator translator =
      new BedrockTranslator(
          json,
          new AnthropicTranslator(json, new AnthropicProperties(false, null, null, null, 1024)),
          new BedrockProperties(true, "us-east-1", "ak", "sk", null, null, 512));

  @Test
  void buildsBedrockBodyWithoutModelAndWithAnthropicVersion() throws Exception {
    byte[] openAi =
        ("{\"model\":\"bedrock/anthropic.claude-3-5-sonnet-20240620-v1:0\",\"messages\":"
                + "[{\"role\":\"system\",\"content\":\"be terse\"},"
                + "{\"role\":\"user\",\"content\":\"hi\"}]}")
            .getBytes(StandardCharsets.UTF_8);

    JsonNode out = json.readTree(translator.toBedrockRequest(openAi));

    assertThat(out.has("model")).isFalse(); // model id travels in the URL, not the body
    assertThat(out.get("anthropic_version").asText()).isEqualTo("bedrock-2023-05-31");
    assertThat(out.get("max_tokens").asInt()).isEqualTo(512); // defaulted from properties
    assertThat(out.get("system").asText()).isEqualTo("be terse");
    assertThat(out.get("messages")).hasSize(1);
    assertThat(out.get("messages").get(0).get("role").asText()).isEqualTo("user");
  }

  @Test
  void translatesBedrockResponseBackToOpenAiShape() {
    String bedrock =
        "{\"id\":\"msg_1\",\"model\":\"claude\",\"content\":[{\"type\":\"text\","
            + "\"text\":\"hello\"}],\"stop_reason\":\"end_turn\","
            + "\"usage\":{\"input_tokens\":3,\"output_tokens\":2}}";

    String openAi = translator.toOpenAiResponse(bedrock);

    assertThat(openAi).contains("\"object\":\"chat.completion\"");
    assertThat(openAi).contains("\"content\":\"hello\"");
    assertThat(openAi).contains("\"total_tokens\":5");
  }
}

package com.gatewise.gateway.proxy;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatewise.gateway.config.AnthropicProperties;
import org.junit.jupiter.api.Test;

/** Verifies the OpenAI ⇄ Anthropic translation in both directions. */
class AnthropicTranslatorTest {

  private final ObjectMapper json = new ObjectMapper();
  private final AnthropicTranslator translator =
      new AnthropicTranslator(json, new AnthropicProperties(true, null, null, null, 1024));

  @Test
  void translatesRequestToAnthropicShape() throws Exception {
    String openai =
        "{\"model\":\"anthropic/claude-3-5-sonnet-20241022\",\"messages\":["
            + "{\"role\":\"system\",\"content\":\"Be brief.\"},"
            + "{\"role\":\"user\",\"content\":\"Hi\"}],\"temperature\":0.5}";

    JsonNode out = json.readTree(translator.toAnthropicRequest(openai.getBytes(UTF_8)));

    assertThat(out.get("model").asText())
        .isEqualTo("claude-3-5-sonnet-20241022"); // prefix stripped
    assertThat(out.get("max_tokens").asInt()).isEqualTo(1024); // defaulted (Anthropic requires it)
    assertThat(out.get("system").asText()).isEqualTo("Be brief."); // system hoisted to top level
    assertThat(out.get("messages").size()).isEqualTo(1); // only the non-system message remains
    assertThat(out.get("messages").get(0).get("role").asText()).isEqualTo("user");
    assertThat(out.get("temperature").asDouble()).isEqualTo(0.5);
  }

  @Test
  void translatesResponseBackToOpenAiShape() throws Exception {
    String anthropic =
        "{\"id\":\"msg_1\",\"model\":\"claude-3-5-sonnet-20241022\",\"content\":["
            + "{\"type\":\"text\",\"text\":\"Hello\"},{\"type\":\"text\",\"text\":\" world\"}],"
            + "\"stop_reason\":\"end_turn\",\"usage\":{\"input_tokens\":10,\"output_tokens\":3}}";

    JsonNode out = json.readTree(translator.toOpenAiResponse(anthropic));

    assertThat(out.get("object").asText()).isEqualTo("chat.completion");
    assertThat(out.get("choices").get(0).get("message").get("content").asText())
        .isEqualTo("Hello world");
    assertThat(out.get("choices").get(0).get("finish_reason").asText()).isEqualTo("stop");
    assertThat(out.get("usage").get("prompt_tokens").asInt()).isEqualTo(10);
    assertThat(out.get("usage").get("completion_tokens").asInt()).isEqualTo(3);
    assertThat(out.get("usage").get("total_tokens").asInt()).isEqualTo(13);
  }
}

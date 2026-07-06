package com.gatewise.gateway.proxy;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatewise.gateway.config.GeminiProperties;
import org.junit.jupiter.api.Test;

/** Verifies the OpenAI ⇄ Gemini translation in both directions. */
class GeminiTranslatorTest {

  private final ObjectMapper json = new ObjectMapper();
  private final GeminiTranslator translator =
      new GeminiTranslator(json, new GeminiProperties(true, null, null, 1024));

  @Test
  void translatesRequestToGeminiShape() throws Exception {
    String openai =
        "{\"model\":\"google/gemini-1.5-pro\",\"messages\":["
            + "{\"role\":\"system\",\"content\":\"Be brief.\"},"
            + "{\"role\":\"user\",\"content\":\"Hi\"},"
            + "{\"role\":\"assistant\",\"content\":\"Hello\"}],\"temperature\":0.5}";

    JsonNode out = json.readTree(translator.toGeminiRequest(openai.getBytes(UTF_8)));

    // System message hoisted to top-level systemInstruction (not a turn).
    assertThat(out.get("systemInstruction").get("parts").get(0).get("text").asText())
        .isEqualTo("Be brief.");
    assertThat(out.get("contents").size()).isEqualTo(2); // only the non-system turns remain
    assertThat(out.get("contents").get(0).get("role").asText()).isEqualTo("user");
    assertThat(out.get("contents").get(0).get("parts").get(0).get("text").asText()).isEqualTo("Hi");
    assertThat(out.get("contents").get(1).get("role").asText())
        .isEqualTo("model"); // assistant→model
    assertThat(out.get("generationConfig").get("maxOutputTokens").asInt()).isEqualTo(1024);
    assertThat(out.get("generationConfig").get("temperature").asDouble()).isEqualTo(0.5);
  }

  @Test
  void translatesResponseBackToOpenAiShape() throws Exception {
    String gemini =
        "{\"modelVersion\":\"gemini-1.5-pro\",\"candidates\":[{\"content\":{\"parts\":["
            + "{\"text\":\"Hello\"},{\"text\":\" world\"}]},\"finishReason\":\"STOP\"}],"
            + "\"usageMetadata\":{\"promptTokenCount\":10,\"candidatesTokenCount\":3,"
            + "\"totalTokenCount\":13}}";

    JsonNode out = json.readTree(translator.toOpenAiResponse(gemini));

    assertThat(out.get("object").asText()).isEqualTo("chat.completion");
    assertThat(out.get("choices").get(0).get("message").get("content").asText())
        .isEqualTo("Hello world"); // parts joined
    assertThat(out.get("choices").get(0).get("finish_reason").asText()).isEqualTo("stop");
    assertThat(out.get("usage").get("prompt_tokens").asInt()).isEqualTo(10);
    assertThat(out.get("usage").get("completion_tokens").asInt()).isEqualTo(3);
    assertThat(out.get("usage").get("total_tokens").asInt()).isEqualTo(13);
  }
}

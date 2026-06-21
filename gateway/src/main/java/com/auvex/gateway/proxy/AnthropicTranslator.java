package com.auvex.gateway.proxy;

import com.auvex.gateway.config.AnthropicProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

/**
 * Translates between the OpenAI chat-completions shape (the gateway's canonical form) and
 * Anthropic's native {@code /v1/messages} shape, in both directions.
 *
 * <p>Because the response is translated back to the OpenAI shape, everything downstream (response
 * redaction, token/cost accounting, the audit record) works unchanged — it only ever sees OpenAI
 * JSON, whichever provider actually served the call.
 */
@Component
public class AnthropicTranslator {

  private static final String ANTHROPIC_PREFIX = "anthropic/";

  private final ObjectMapper json;
  private final AnthropicProperties properties;

  public AnthropicTranslator(ObjectMapper json, AnthropicProperties properties) {
    this.json = json;
    this.properties = properties;
  }

  /** OpenAI chat-completions request bytes → Anthropic messages request bytes. */
  public byte[] toAnthropicRequest(byte[] openAiRequest) {
    try {
      JsonNode in = json.readTree(openAiRequest);
      ObjectNode out = json.createObjectNode();

      String model = in.path("model").asText("");
      out.put(
          "model",
          model.startsWith(ANTHROPIC_PREFIX) ? model.substring(ANTHROPIC_PREFIX.length()) : model);
      // Anthropic requires max_tokens; default it when the caller didn't supply one.
      out.put(
          "max_tokens",
          in.has("max_tokens") ? in.get("max_tokens").asInt() : properties.maxTokens());
      if (in.hasNonNull("temperature")) {
        out.set("temperature", in.get("temperature"));
      }
      if (in.path("stream").asBoolean(false)) {
        out.put("stream", true);
      }

      // Anthropic carries the system prompt at the top level, not as a message role.
      ArrayNode messages = json.createArrayNode();
      StringBuilder system = new StringBuilder();
      for (JsonNode message : in.path("messages")) {
        String role = message.path("role").asText();
        JsonNode content = message.path("content");
        if ("system".equals(role)) {
          if (system.length() > 0) {
            system.append('\n');
          }
          system.append(content.asText());
        } else {
          ObjectNode translated = json.createObjectNode();
          translated.put("role", role);
          translated.set("content", content);
          messages.add(translated);
        }
      }
      if (system.length() > 0) {
        out.put("system", system.toString());
      }
      out.set("messages", messages);
      return json.writeValueAsBytes(out);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to translate request to Anthropic", e);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Failed to read request for Anthropic translation", e);
    }
  }

  /** Anthropic messages response → OpenAI chat-completions response JSON. */
  public String toOpenAiResponse(String anthropicBody) {
    try {
      JsonNode in = json.readTree(anthropicBody);

      StringBuilder text = new StringBuilder();
      for (JsonNode block : in.path("content")) {
        if ("text".equals(block.path("type").asText())) {
          text.append(block.path("text").asText());
        }
      }

      ObjectNode out = json.createObjectNode();
      out.put("id", in.path("id").asText(""));
      out.put("object", "chat.completion");
      out.put("model", in.path("model").asText(""));

      ObjectNode message = json.createObjectNode();
      message.put("role", "assistant");
      message.put("content", text.toString());
      ObjectNode choice = json.createObjectNode();
      choice.put("index", 0);
      choice.set("message", message);
      choice.put("finish_reason", finishReason(in.path("stop_reason").asText("end_turn")));
      ArrayNode choices = json.createArrayNode();
      choices.add(choice);
      out.set("choices", choices);

      JsonNode usage = in.path("usage");
      if (usage.isObject()) {
        int input = usage.path("input_tokens").asInt(0);
        int output = usage.path("output_tokens").asInt(0);
        ObjectNode translated = json.createObjectNode();
        translated.put("prompt_tokens", input);
        translated.put("completion_tokens", output);
        translated.put("total_tokens", input + output);
        out.set("usage", translated);
      }
      return json.writeValueAsString(out);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to translate Anthropic response", e);
    }
  }

  private static String finishReason(String stopReason) {
    return "max_tokens".equals(stopReason) ? "length" : "stop";
  }
}

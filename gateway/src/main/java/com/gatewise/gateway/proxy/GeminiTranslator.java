package com.gatewise.gateway.proxy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gatewise.gateway.config.GeminiProperties;
import org.springframework.stereotype.Component;

/**
 * Translates between the OpenAI chat-completions shape (the gateway's canonical form) and Gemini's
 * native {@code generateContent} shape, in both directions.
 *
 * <p>Because the response is translated back to the OpenAI shape, everything downstream (response
 * redaction, token/cost accounting, the audit record) works unchanged — it only ever sees OpenAI
 * JSON, whichever provider actually served the call.
 */
@Component
public class GeminiTranslator {

  private final ObjectMapper json;
  private final GeminiProperties properties;

  public GeminiTranslator(ObjectMapper json, GeminiProperties properties) {
    this.json = json;
    this.properties = properties;
  }

  /** OpenAI chat-completions request bytes → Gemini generateContent request bytes. */
  public byte[] toGeminiRequest(byte[] openAiRequest) {
    try {
      JsonNode in = json.readTree(openAiRequest);
      ObjectNode out = json.createObjectNode();

      // Gemini carries the system prompt at the top level, not as a turn role.
      ArrayNode contents = json.createArrayNode();
      StringBuilder system = new StringBuilder();
      for (JsonNode message : in.path("messages")) {
        String role = message.path("role").asText();
        String content = message.path("content").asText();
        if ("system".equals(role)) {
          if (system.length() > 0) {
            system.append('\n');
          }
          system.append(content);
        } else {
          ObjectNode turn = json.createObjectNode();
          turn.put("role", "assistant".equals(role) ? "model" : "user");
          ArrayNode parts = json.createArrayNode();
          ObjectNode part = json.createObjectNode();
          part.put("text", content);
          parts.add(part);
          turn.set("parts", parts);
          contents.add(turn);
        }
      }
      out.set("contents", contents);

      if (system.length() > 0) {
        ObjectNode instruction = json.createObjectNode();
        ArrayNode parts = json.createArrayNode();
        ObjectNode part = json.createObjectNode();
        part.put("text", system.toString());
        parts.add(part);
        instruction.set("parts", parts);
        out.set("systemInstruction", instruction);
      }

      ObjectNode generationConfig = json.createObjectNode();
      generationConfig.put(
          "maxOutputTokens",
          in.has("max_tokens") ? in.get("max_tokens").asInt() : properties.maxTokens());
      if (in.hasNonNull("temperature")) {
        generationConfig.set("temperature", in.get("temperature"));
      }
      out.set("generationConfig", generationConfig);

      return json.writeValueAsBytes(out);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to translate request to Gemini", e);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Failed to read request for Gemini translation", e);
    }
  }

  /** Gemini generateContent response → OpenAI chat-completions response JSON. */
  public String toOpenAiResponse(String geminiBody) {
    try {
      JsonNode in = json.readTree(geminiBody);
      JsonNode candidate = in.path("candidates").path(0);

      StringBuilder text = new StringBuilder();
      for (JsonNode part : candidate.path("content").path("parts")) {
        text.append(part.path("text").asText());
      }

      ObjectNode out = json.createObjectNode();
      out.put("id", in.path("responseId").asText(""));
      out.put("object", "chat.completion");
      out.put("model", in.path("modelVersion").asText(""));

      ObjectNode message = json.createObjectNode();
      message.put("role", "assistant");
      message.put("content", text.toString());
      ObjectNode choice = json.createObjectNode();
      choice.put("index", 0);
      choice.set("message", message);
      choice.put("finish_reason", finishReason(candidate.path("finishReason").asText("STOP")));
      ArrayNode choices = json.createArrayNode();
      choices.add(choice);
      out.set("choices", choices);

      JsonNode usage = in.path("usageMetadata");
      if (usage.isObject()) {
        int input = usage.path("promptTokenCount").asInt(0);
        int output = usage.path("candidatesTokenCount").asInt(0);
        ObjectNode translated = json.createObjectNode();
        translated.put("prompt_tokens", input);
        translated.put("completion_tokens", output);
        translated.put("total_tokens", usage.path("totalTokenCount").asInt(input + output));
        out.set("usage", translated);
      }
      return json.writeValueAsString(out);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to translate Gemini response", e);
    }
  }

  private static String finishReason(String reason) {
    return "MAX_TOKENS".equals(reason) ? "length" : "stop";
  }
}

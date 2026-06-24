package com.auvex.gateway.proxy;

import com.auvex.gateway.config.BedrockProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

/**
 * Adapts a request for Amazon Bedrock's Claude models.
 *
 * <p>Bedrock's {@code InvokeModel} body for an Anthropic model is the Anthropic Messages body with
 * two differences: there is no top-level {@code model} (the model id lives in the URL), and the API
 * version travels in the body as {@code anthropic_version} rather than as a header. So this reuses
 * {@link AnthropicTranslator} for the heavy lifting and applies just those two adjustments. The
 * response is the Anthropic Messages shape, so {@link AnthropicTranslator#toOpenAiResponse} converts
 * it back to the canonical OpenAI shape unchanged.
 */
@Component
public class BedrockTranslator {

  private final ObjectMapper json;
  private final AnthropicTranslator anthropic;
  private final BedrockProperties properties;

  public BedrockTranslator(
      ObjectMapper json, AnthropicTranslator anthropic, BedrockProperties properties) {
    this.json = json;
    this.anthropic = anthropic;
    this.properties = properties;
  }

  /** OpenAI chat-completions request bytes → Bedrock InvokeModel (Anthropic) request bytes. */
  public byte[] toBedrockRequest(byte[] openAiRequest) {
    try {
      JsonNode original = json.readTree(openAiRequest);
      JsonNode anthropicBody = json.readTree(anthropic.toAnthropicRequest(openAiRequest));
      ObjectNode out = anthropicBody.deepCopy();
      out.remove("model"); // the model id is carried in the request URL, not the body
      out.put("anthropic_version", properties.anthropicVersion());
      // Apply the Bedrock-specific max_tokens default only when the caller didn't set one (the
      // shared Anthropic translation already defaulted it, so this overrides that default).
      if (!original.has("max_tokens")) {
        out.put("max_tokens", properties.maxTokens());
      }
      return json.writeValueAsBytes(out);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to translate request for Bedrock", e);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Failed to read request for Bedrock translation", e);
    }
  }

  /** Bedrock (Anthropic) response body → OpenAI chat-completions response JSON. */
  public String toOpenAiResponse(String bedrockBody) {
    return anthropic.toOpenAiResponse(bedrockBody);
  }
}

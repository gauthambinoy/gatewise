package com.auvex.gateway.multimodal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

/**
 * Finds image parts in a multimodal chat request and applies the configured policy. Images can
 * carry PII the text pipeline never sees — a photographed document, a screenshot — so an operator
 * can keep them out entirely (STRIP) or refuse the request (BLOCK); the default is to forward but
 * audit.
 *
 * <p>This is deliberately dependency-free: it governs image egress (count / strip / block) rather
 * than running OCR. Pixel-level inspection would need an OCR or vision service and is left as a
 * hook.
 */
@Component
public class ImageScanner {

  private final MultimodalProperties properties;

  public ImageScanner(MultimodalProperties properties) {
    this.properties = properties;
  }

  /** Counts image parts, strips them when the policy says so, and flags a block when required. */
  public ScanResult scan(JsonNode body) {
    JsonNode messages = body == null ? null : body.get("messages");
    if (messages == null || !messages.isArray()) {
      return new ScanResult(0, false);
    }
    int images = 0;
    for (JsonNode message : messages) {
      JsonNode content = message.get("content");
      if (content == null || !content.isArray()) {
        continue;
      }
      for (JsonNode part : content) {
        if (isImagePart(part)) {
          images++;
          if (properties.policy() == MultimodalProperties.Policy.STRIP) {
            ObjectNode partObject = (ObjectNode) part;
            partObject.removeAll();
            partObject.put("type", "text");
            partObject.put("text", "‹IMAGE_REMOVED›");
          }
        }
      }
    }
    boolean blocked = images > 0 && properties.policy() == MultimodalProperties.Policy.BLOCK;
    return new ScanResult(images, blocked);
  }

  // An OpenAI-style image part: {"type":"image_url", ...}; also accept the Responses "input_image".
  private static boolean isImagePart(JsonNode part) {
    if (part == null || !part.isObject()) {
      return false;
    }
    String type = part.path("type").asText("");
    return type.equals("image_url") || type.equals("input_image");
  }

  /** The outcome of an image scan: how many images were seen and whether to block the request. */
  public record ScanResult(int imageCount, boolean blocked) {}
}

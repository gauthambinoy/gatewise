package com.auvex.gateway.web;

import com.auvex.gateway.injection.InjectionFinding;
import com.auvex.gateway.injection.InjectionScanner;
import com.auvex.gateway.moderation.ContentModerationScanner;
import com.auvex.gateway.redaction.Match;
import com.auvex.gateway.redaction.RedactionEngine;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A native moderation check: screens text for sensitive data and prompt-injection locally, using
 * the same detectors and injection rules as the gateway — no external provider call, nothing leaves
 * the gateway. Apps can pre-screen content before sending it anywhere.
 */
@RestController
@RequestMapping("/v1")
public class ModerationController {

  private final RedactionEngine redaction;
  private final InjectionScanner injectionScanner;
  private final ContentModerationScanner moderationScanner;

  public ModerationController(
      RedactionEngine redaction,
      InjectionScanner injectionScanner,
      ContentModerationScanner moderationScanner) {
    this.redaction = redaction;
    this.injectionScanner = injectionScanner;
    this.moderationScanner = moderationScanner;
  }

  /** Reports the sensitive data and injection categories found in {@code input}. */
  @PostMapping("/moderations")
  public ModerationResult moderate(@RequestBody JsonNode body) {
    String input = inputOf(body);

    Map<String, Integer> sensitive = new HashMap<>();
    for (Match match : redaction.redact(input).matches()) {
      sensitive.merge(match.type().name().toLowerCase(Locale.ROOT), 1, Integer::sum);
    }
    List<String> injection =
        injectionScanner.scan(input).stream().map(InjectionFinding::category).distinct().toList();
    List<String> moderation = moderationScanner.categories(input);

    boolean flagged = !sensitive.isEmpty() || !injection.isEmpty() || !moderation.isEmpty();
    return new ModerationResult(flagged, sensitive, injection, moderation);
  }

  private static String inputOf(JsonNode body) {
    if (body == null
        || !body.isObject()
        || body.get("input") == null
        || !body.get("input").isTextual()) {
      throw new InvalidRequestException("'input' is required and must be a string.");
    }
    return body.get("input").asText();
  }
}

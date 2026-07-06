package com.gatewise.gateway.web;

import java.util.List;
import java.util.Map;

/**
 * The result of a native moderation check.
 *
 * @param flagged whether anything sensitive, adversarial or unsafe was found
 * @param sensitiveData counts of detected PII / secrets by type (e.g. {@code {"email":1}})
 * @param injection the prompt-injection categories detected (e.g. {@code ["instruction_override"]})
 * @param moderation the content-safety categories detected (e.g. {@code ["self-harm"]})
 */
public record ModerationResult(
    boolean flagged,
    Map<String, Integer> sensitiveData,
    List<String> injection,
    List<String> moderation) {}

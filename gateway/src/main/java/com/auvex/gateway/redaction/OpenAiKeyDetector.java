package com.auvex.gateway.redaction;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Detects OpenAI API keys — both legacy {@code sk-} and project-scoped {@code sk-proj-} keys.
 *
 * <p>Anchored on {@code sk-} with a dash; Stripe secret keys use {@code sk_} with an underscore, so
 * the two never clash. High priority (9) so these high-entropy secrets win overlaps with the
 * generic ApiKeyDetector.
 */
@Component
public class OpenAiKeyDetector extends RegexDetector {

  private static final Pattern OPENAI_KEY =
      Pattern.compile("\\bsk-(?:proj-)?[A-Za-z0-9_-]{20,}\\b");

  public OpenAiKeyDetector() {
    super(PiiType.OPENAI_KEY, 9, OPENAI_KEY);
  }
}

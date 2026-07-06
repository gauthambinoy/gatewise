package com.gatewise.gateway.moderation;

import org.springframework.stereotype.Component;

/**
 * Flags self-harm and suicidal-intent language. Intent- and instruction-focused so ordinary
 * discussion ("suicide prevention hotline") is far less likely to trip than a direct request.
 */
@Component
public class SelfHarmRule extends RegexModerationRule {

  public SelfHarmRule() {
    super(
        ModerationCategory.SELF_HARM,
        ci("\\bhow\\s+(can|do)\\s+i\\s+(kill|hurt|harm)\\s+myself\\b"),
        ci("\\b(kill|hurt|harm)\\s+myself\\b"),
        ci("\\b(commit|committing)\\s+suicide\\b"),
        ci("\\b(end|ending|take)\\s+my\\s+(own\\s+)?life\\b"),
        ci("\\b(want|going)\\s+to\\s+(die|kill\\s+myself)\\b"),
        ci("\\bcut(ting)?\\s+myself\\b"),
        ci("\\bself[\\s-]?harm\\b"));
  }
}

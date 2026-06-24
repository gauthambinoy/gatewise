package com.auvex.gateway.moderation;

import org.springframework.stereotype.Component;

/**
 * Flags hateful, group-targeted, eliminationist intent. Rather than enumerate slurs, this matches
 * the dehumanising/eliminationist phrasing aimed at a group — a more stable, less brittle signal.
 * Crude by nature; a model classifier is the right tool for production-grade hate detection.
 */
@Component
public class HateRule extends RegexModerationRule {

  public HateRule() {
    super(
        ModerationCategory.HATE,
        ci("\\b(exterminate|eradicate|gas|lynch|cleanse)\\s+(all\\s+)?(the\\s+)?\\w+s\\b"),
        ci("\\ball\\s+\\w+\\s+(should|must)\\s+(die|be\\s+(killed|eliminated|removed))\\b"),
        ci("\\b\\w+\\s+are\\s+(subhuman|vermin|animals|a\\s+disease)\\b"),
        ci("\\b(hate|kill)\\s+all\\s+(of\\s+)?(the\\s+)?\\w+\\s+people\\b"));
  }
}

package com.gatewise.gateway.moderation;

import org.springframework.stereotype.Component;

/** Flags directed insults and personal threats ("I will find you and hurt you"). */
@Component
public class HarassmentRule extends RegexModerationRule {

  public HarassmentRule() {
    super(
        ModerationCategory.HARASSMENT,
        ci("\\bi\\s+(will|'?ll|am\\s+going\\s+to)\\s+(find|hunt|hurt|kill|destroy)\\s+you\\b"),
        ci(
            "\\byou\\s+(are|'?re)\\s+(a|an|such\\s+a)\\s+"
                + "(idiot|moron|loser|worthless|pathetic|disgusting)\\b"),
        ci("\\b(kill|kys)\\s+yourself\\b"),
        ci("\\bnobody\\s+(likes|wants)\\s+you\\b"),
        ci("\\bi\\s+hope\\s+you\\s+(die|suffer)\\b"));
  }
}

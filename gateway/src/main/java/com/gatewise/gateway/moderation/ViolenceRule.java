package com.gatewise.gateway.moderation;

import org.springframework.stereotype.Component;

/**
 * Flags violent intent and weapon/explosive-construction requests — the classic "how to build a
 * bomb" and direct threats-to-others patterns.
 */
@Component
public class ViolenceRule extends RegexModerationRule {

  public ViolenceRule() {
    super(
        ModerationCategory.VIOLENCE,
        ci("\\bhow\\s+to\\s+(make|build|construct)\\s+a?\\s*(bomb|explosive|gun|weapon)\\b"),
        ci("\\b(build|make)\\s+a\\s+(pipe\\s+)?bomb\\b"),
        ci("\\bkill\\s+(him|her|them|people|everyone|as\\s+many)\\b"),
        ci("\\bhow\\s+to\\s+(kill|murder|poison)\\s+(someone|a\\s+person|people)\\b"),
        ci("\\b(shoot|stab|behead)\\s+(up\\s+)?(the|a|my|those)\\b"),
        ci("\\bmass\\s+(shooting|casualt)\\w*\\b"));
  }
}

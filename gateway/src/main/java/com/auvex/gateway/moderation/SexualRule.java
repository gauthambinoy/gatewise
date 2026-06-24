package com.auvex.gateway.moderation;

import org.springframework.stereotype.Component;

/**
 * Flags explicit sexual solicitation, with particular weight on the safety-critical case of sexual
 * content involving minors. Kept deliberately narrow and non-graphic; a model classifier is the
 * right tool for nuanced sexual-content moderation.
 */
@Component
public class SexualRule extends RegexModerationRule {

  public SexualRule() {
    super(
        ModerationCategory.SEXUAL,
        // Safety-critical: any sexual framing combined with a minor reference.
        ci("\\b(child|children|minor|underage|teen|kid)\\b[^.?!]{0,40}\\bsex\\w*\\b"),
        ci("\\bsex\\w*\\b[^.?!]{0,40}\\b(child|children|minor|underage|kid)\\b"),
        ci("\\bwrite\\s+(me\\s+)?(an?\\s+)?(explicit|erotic)\\s+(story|scene|roleplay)\\b"),
        ci("\\bsexually\\s+explicit\\b"));
  }
}

package com.auvex.gateway.redaction;

import org.springframework.stereotype.Component;

/**
 * Replaces each match with a deterministic, typed placeholder token.
 *
 * <p>Tokens are wrapped in U+2039/U+203A angle quotes (‹ ›) so they never collide with {@code
 * <...>} in a user's HTML/JSON and are trivially greppable. The same input always yields the same
 * output.
 */
@Component
public class TokenMasker implements Masker {

  @Override
  public String tokenFor(PiiType type) {
    return switch (type) {
      case EMAIL -> "‹EMAIL_REDACTED›";
      case CREDIT_CARD -> "‹CARD_REDACTED›";
      case IBAN -> "‹IBAN_REDACTED›";
      case AWS_ACCESS_KEY_ID -> "‹AWS_KEY_ID_REDACTED›";
      case AWS_SECRET_KEY -> "‹AWS_SECRET_REDACTED›";
      case API_KEY -> "‹API_KEY_REDACTED›";
      case JWT -> "‹JWT_REDACTED›";
      case PEM_PRIVATE_KEY -> "‹PRIVATE_KEY_REDACTED›";
    };
  }
}

package com.gatewise.gateway.redaction;

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
      case US_SSN -> "‹SSN_REDACTED›";
      case PHONE -> "‹PHONE_REDACTED›";
      case IP_ADDRESS -> "‹IP_REDACTED›";
      case MAC_ADDRESS -> "‹MAC_REDACTED›";
      case GITHUB_TOKEN -> "‹GITHUB_TOKEN_REDACTED›";
      case SLACK_TOKEN -> "‹SLACK_TOKEN_REDACTED›";
      case GOOGLE_API_KEY -> "‹GOOGLE_API_KEY_REDACTED›";
      case STRIPE_KEY -> "‹STRIPE_KEY_REDACTED›";
      case OPENAI_KEY -> "‹OPENAI_KEY_REDACTED›";
      case UK_NINO -> "‹UK_NINO_REDACTED›";
      case INDIA_PAN -> "‹PAN_REDACTED›";
      case INDIA_AADHAAR -> "‹AADHAAR_REDACTED›";
      case CANADA_SIN -> "‹SIN_REDACTED›";
      case US_ITIN -> "‹ITIN_REDACTED›";
      case AU_TFN -> "‹TFN_REDACTED›";
      case PERSON_NAME -> "‹NAME_REDACTED›";
      case STREET_ADDRESS -> "‹ADDRESS_REDACTED›";
      case DATE_OF_BIRTH -> "‹DOB_REDACTED›";
      case PASSPORT -> "‹PASSPORT_REDACTED›";
      case MEDICAL_RECORD_NUMBER -> "‹MRN_REDACTED›";
      case CUSTOM -> "‹CUSTOM_REDACTED›";
    };
  }
}

package com.auvex.gateway.redaction;

/** Categories of sensitive data the gateway detects and masks before a prompt leaves. */
public enum PiiType {
  EMAIL,
  CREDIT_CARD,
  IBAN,
  AWS_ACCESS_KEY_ID,
  AWS_SECRET_KEY,
  API_KEY,
  JWT,
  PEM_PRIVATE_KEY,
  // Personal identifiers
  US_SSN,
  PHONE,
  IP_ADDRESS,
  MAC_ADDRESS,
  // Provider secrets / tokens
  GITHUB_TOKEN,
  SLACK_TOKEN,
  GOOGLE_API_KEY,
  STRIPE_KEY,
  OPENAI_KEY,
  // National / government identifiers (global compliance)
  UK_NINO,
  INDIA_PAN,
  INDIA_AADHAAR,
  CANADA_SIN,
  US_ITIN,
  AU_TFN,
  // Unstructured / contextual PII (context-anchored to keep precision high)
  PERSON_NAME,
  STREET_ADDRESS,
  DATE_OF_BIRTH,
  PASSPORT,
  MEDICAL_RECORD_NUMBER
}

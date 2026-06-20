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
  PEM_PRIVATE_KEY
}

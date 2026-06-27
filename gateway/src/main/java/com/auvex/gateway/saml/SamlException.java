package com.auvex.gateway.saml;

/** Raised when a SAML sign-in fails verification (bad signature, audience, time window, …). */
public class SamlException extends RuntimeException {

  public SamlException(String message) {
    super(message);
  }

  public SamlException(String message, Throwable cause) {
    super(message, cause);
  }
}

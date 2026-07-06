package com.gatewise.gateway.oidc;

/** Raised when an OIDC sign-in fails (bad state, token exchange error, invalid id_token). */
public class OidcException extends RuntimeException {

  public OidcException(String message) {
    super(message);
  }

  public OidcException(String message, Throwable cause) {
    super(message, cause);
  }
}

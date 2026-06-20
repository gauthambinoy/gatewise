package com.auvex.gateway.policy;

/** Raised when a tenant's policy refuses a request. Carries the human-readable reason. */
public class PolicyDeniedException extends RuntimeException {

  public PolicyDeniedException(String reason) {
    super(reason);
  }
}

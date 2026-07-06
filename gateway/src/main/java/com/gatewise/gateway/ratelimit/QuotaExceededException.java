package com.gatewise.gateway.ratelimit;

/** Raised when a per-user or per-model daily quota is exhausted. Maps to HTTP 429. */
public class QuotaExceededException extends RuntimeException {

  public QuotaExceededException(String message) {
    super(message);
  }
}

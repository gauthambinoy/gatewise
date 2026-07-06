package com.gatewise.gateway.proxy;

/** Raised when the upstream model provider can't be reached or doesn't respond in time. */
public class UpstreamUnavailableException extends RuntimeException {

  public UpstreamUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}

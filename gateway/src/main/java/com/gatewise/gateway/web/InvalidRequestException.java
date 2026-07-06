package com.gatewise.gateway.web;

/** Raised when an incoming request is well-formed JSON but not a valid chat-completions payload. */
public class InvalidRequestException extends RuntimeException {

  public InvalidRequestException(String message) {
    super(message);
  }
}

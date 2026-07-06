package com.gatewise.gateway.web;

/** Raised when a requested resource doesn't exist for the current tenant. */
public class NotFoundException extends RuntimeException {

  public NotFoundException(String message) {
    super(message);
  }
}

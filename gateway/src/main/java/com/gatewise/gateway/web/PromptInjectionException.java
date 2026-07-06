package com.gatewise.gateway.web;

/** Thrown when a request is blocked because the prompt looks like an injection / jailbreak. */
public class PromptInjectionException extends RuntimeException {

  public PromptInjectionException(String message) {
    super(message);
  }
}

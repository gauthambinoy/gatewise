package com.gatewise.gateway.multimodal;

/** Thrown when a request is rejected because it carries image content the policy forbids. */
public class MultimodalBlockedException extends RuntimeException {

  public MultimodalBlockedException(String message) {
    super(message);
  }
}

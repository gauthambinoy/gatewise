package com.auvex.gateway.web;

/** Raised when a tenant has used up its call budget for the current window. */
public class BudgetExceededException extends RuntimeException {

  public BudgetExceededException(String message) {
    super(message);
  }
}

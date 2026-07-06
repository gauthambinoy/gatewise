package com.gatewise.gateway.residency;

/**
 * Thrown when a request would send a tenant's data to a model outside the region the tenant's data
 * residency is pinned to. Surfaced as a 403 by the gateway exception handler.
 */
public class DataResidencyException extends RuntimeException {

  public DataResidencyException(String message) {
    super(message);
  }
}

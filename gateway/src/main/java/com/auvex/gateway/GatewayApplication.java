package com.auvex.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Boots the Auvex gateway.
 *
 * <p>The gateway is the single controlled passage every enterprise LLM call flows through. Over the
 * coming roadmap slices it will redact sensitive data before a prompt leaves, route the request to
 * the chosen model, enforce per-tenant policy, and write an immutable audit record. This class is
 * just the entry point that stands up the Spring context all of that hangs off.
 */
@SpringBootApplication
public class GatewayApplication {

  /** Starts the Spring context and, in later slices, the gateway HTTP server. */
  public static void main(String[] args) {
    SpringApplication.run(GatewayApplication.class, args);
  }
}

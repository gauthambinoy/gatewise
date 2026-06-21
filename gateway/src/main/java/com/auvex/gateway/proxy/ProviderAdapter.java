package com.auvex.gateway.proxy;

/**
 * A provider the gateway can forward a (buffered) chat-completions request to.
 *
 * <p>Adapters take and return the OpenAI shape — the gateway's canonical form — so every adapter
 * translates to and from its provider's native API internally. {@link UpstreamProxy} asks each
 * adapter (in {@code @Order}) whether it {@link #supports} the resolved model and uses the first
 * that does; an OpenAI-compatible adapter is the lowest-precedence catch-all. Adding a provider is
 * just adding an {@code @Component} adapter — no change to the proxy.
 */
public interface ProviderAdapter {

  /** Whether this adapter handles the given resolved provider model (e.g. {@code anthropic/…}). */
  boolean supports(String model);

  /** Sends the OpenAI-shaped request and returns an OpenAI-shaped buffered response. */
  CachedResponse fetch(byte[] openAiRequest);
}

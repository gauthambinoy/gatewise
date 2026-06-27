package com.auvex.gateway.proxy;

/**
 * A provider the gateway can forward a (buffered) embeddings request to.
 *
 * <p>Mirrors {@link ProviderAdapter} for the embeddings path: adapters take and return the OpenAI
 * embeddings shape — {@code {model, input}} in, {@code {object:"list", data:[{embedding, index}],
 * model, usage}} out — so every adapter translates to and from its provider's native embeddings API
 * internally. {@link EmbeddingsProxy} asks each adapter (in {@code @Order}) whether it {@link
 * #supports} the resolved model and uses the first that does; an OpenAI-compatible adapter is the
 * lowest-precedence catch-all. Adding a provider is just adding an {@code @Component} adapter — no
 * change to the proxy.
 */
public interface EmbeddingsAdapter {

  /** Whether this adapter handles the given resolved provider model (e.g. {@code google/…}). */
  boolean supports(String model);

  /** Sends the OpenAI-shaped embeddings request and returns an OpenAI-shaped buffered response. */
  CachedResponse embed(byte[] openAiEmbeddingsRequest);
}

package com.auvex.gateway.cache;

/**
 * Turns text into a fixed-length, L2-normalised vector for similarity search in the semantic cache.
 *
 * <p>Behind an interface so the default dependency-free lexical embedder can be swapped for a model
 * embedder (e.g. calling the provider's embeddings endpoint) without touching the cache itself.
 */
public interface Embedder {

  /** A unit-length vector for the text (all zeros for null/empty). */
  float[] embed(String text);
}

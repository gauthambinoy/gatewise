package com.gatewise.gateway.cache;

import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * A dependency-free, in-process embedder: a hashed bag-of-words (token-frequency) vector, L2
 * normalised so cosine similarity is just a dot product.
 *
 * <p>This gives lexical similarity — near-duplicate and re-worded prompts land close together —
 * without any network call or model. It's the default; a true semantic embedder (model embeddings)
 * can replace it via the {@link Embedder} interface for paraphrase-level matching.
 */
@Component
public class LocalEmbedder implements Embedder {

  private static final int DIMENSIONS = 256;

  @Override
  public float[] embed(String text) {
    float[] vector = new float[DIMENSIONS];
    if (text == null || text.isBlank()) {
      return vector;
    }
    for (String token : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
      if (!token.isEmpty()) {
        vector[Math.floorMod(token.hashCode(), DIMENSIONS)] += 1f;
      }
    }
    double norm = 0;
    for (float v : vector) {
      norm += (double) v * v;
    }
    norm = Math.sqrt(norm);
    if (norm > 0) {
      for (int i = 0; i < DIMENSIONS; i++) {
        vector[i] = (float) (vector[i] / norm);
      }
    }
    return vector;
  }
}

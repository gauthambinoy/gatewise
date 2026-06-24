package com.auvex.gateway.cache;

import com.auvex.gateway.config.SemanticCacheProperties;
import com.auvex.gateway.proxy.CachedResponse;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.stereotype.Component;

/**
 * An in-memory, per-tenant similarity cache. A prompt is embedded to a vector; if a recent prompt's
 * vector is at least {@code threshold} cosine-similar, its stored response is served. Bounded per
 * tenant (oldest evicted), opt-in, and isolated per tenant so one tenant can never read another's
 * cached responses.
 *
 * <p>Complements the exact-match {@link ResponseCache}: that catches identical requests, this
 * catches near-duplicate / re-worded ones.
 */
@Component
public class SemanticCache {

  private final Embedder embedder;
  private final SemanticCacheProperties properties;
  private final Map<UUID, Deque<Entry>> store = new ConcurrentHashMap<>();

  public SemanticCache(Embedder embedder, SemanticCacheProperties properties) {
    this.embedder = embedder;
    this.properties = properties;
  }

  /** Whether the semantic cache should be consulted at all. */
  public boolean enabled() {
    return properties.enabled();
  }

  /** The most similar stored response for this tenant if it clears the threshold, else null. */
  public CachedResponse lookup(UUID tenantId, String promptText) {
    if (!properties.enabled()) {
      return null;
    }
    Deque<Entry> entries = store.get(tenantId);
    if (entries == null || entries.isEmpty()) {
      return null;
    }
    float[] query = embedder.embed(promptText);
    CachedResponse best = null;
    double bestSimilarity = properties.threshold();
    for (Entry entry : entries) {
      double similarity = dot(query, entry.vector());
      if (similarity >= bestSimilarity) {
        bestSimilarity = similarity;
        best = entry.response();
      }
    }
    return best;
  }

  /** Remembers a prompt/response pair for this tenant, evicting the oldest beyond the cap. */
  public void store(UUID tenantId, String promptText, CachedResponse response) {
    if (!properties.enabled()) {
      return;
    }
    Deque<Entry> entries = store.computeIfAbsent(tenantId, k -> new ConcurrentLinkedDeque<>());
    entries.addFirst(new Entry(embedder.embed(promptText), response));
    while (entries.size() > properties.maxEntriesPerTenant()) {
      entries.pollLast();
    }
  }

  // Both vectors are L2-normalised, so the dot product is the cosine similarity.
  private static double dot(float[] a, float[] b) {
    double sum = 0;
    int n = Math.min(a.length, b.length);
    for (int i = 0; i < n; i++) {
      sum += (double) a[i] * b[i];
    }
    return sum;
  }

  private record Entry(float[] vector, CachedResponse response) {}
}

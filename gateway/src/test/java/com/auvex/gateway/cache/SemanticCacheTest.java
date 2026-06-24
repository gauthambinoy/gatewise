package com.auvex.gateway.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.auvex.gateway.config.SemanticCacheProperties;
import com.auvex.gateway.proxy.CachedResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for the semantic cache: near-duplicate hits, unrelated misses, tenant isolation.
 */
class SemanticCacheTest {

  private final CachedResponse response = new CachedResponse(200, "application/json", "{\"ok\":1}");

  private SemanticCache cache(boolean enabled, double threshold) {
    return new SemanticCache(
        new LocalEmbedder(), new SemanticCacheProperties(enabled, threshold, 50));
  }

  @Test
  void servesANearDuplicatePrompt() {
    SemanticCache cache = cache(true, 0.8);
    UUID tenant = UUID.randomUUID();
    cache.store(tenant, "what is the capital city of france", response);

    // A re-worded but lexically overlapping prompt clears the threshold.
    CachedResponse hit = cache.lookup(tenant, "what is the capital city of france please");
    assertThat(hit).isSameAs(response);
  }

  @Test
  void missesAnUnrelatedPrompt() {
    SemanticCache cache = cache(true, 0.8);
    UUID tenant = UUID.randomUUID();
    cache.store(tenant, "what is the capital city of france", response);

    assertThat(cache.lookup(tenant, "explain quantum chromodynamics in detail")).isNull();
  }

  @Test
  void isolatesTenants() {
    SemanticCache cache = cache(true, 0.8);
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    cache.store(a, "the quick brown fox", response);
    // Tenant b has nothing cached, so the same prompt is a miss for them.
    assertThat(cache.lookup(b, "the quick brown fox")).isNull();
  }

  @Test
  void disabledCacheNeverHits() {
    SemanticCache cache = cache(false, 0.8);
    UUID tenant = UUID.randomUUID();
    cache.store(tenant, "the quick brown fox", response);
    assertThat(cache.lookup(tenant, "the quick brown fox")).isNull();
  }

  @Test
  void identicalPromptIsAlwaysAHit() {
    SemanticCache cache = cache(true, 0.95);
    UUID tenant = UUID.randomUUID();
    cache.store(tenant, "summarise this contract clause", response);
    assertThat(cache.lookup(tenant, "summarise this contract clause")).isSameAs(response);
  }
}

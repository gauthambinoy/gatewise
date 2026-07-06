package com.gatewise.gateway.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Data residency / region pinning.
 *
 * <p>When {@code enabled}, a tenant pinned to a region (see {@code Tenant.residencyRegion}) may
 * only reach provider models that run in the same region. {@code modelRegions} maps a provider
 * model — by its exact id or by a prefix the model starts with (e.g. {@code bedrock/}) — to the
 * region code it runs in. Off by default, and a tenant with no pinned region (the default) is never
 * restricted, so the feature is drop-in safe.
 *
 * @param enabled master switch for residency enforcement
 * @param modelRegions provider-model id or prefix → region code (e.g. {@code eu-west-1})
 */
@ConfigurationProperties(prefix = "gatewise.residency")
public record ResidencyProperties(boolean enabled, Map<String, String> modelRegions) {

  public ResidencyProperties {
    modelRegions = modelRegions == null ? Map.of() : Map.copyOf(modelRegions);
  }

  /**
   * The configured region for a provider model: an exact key wins, otherwise the longest configured
   * key that the model starts with; {@code null} when nothing matches (an unknown region).
   */
  public String regionFor(String providerModel) {
    if (providerModel == null) {
      return null;
    }
    String exact = modelRegions.get(providerModel);
    if (exact != null) {
      return exact;
    }
    String bestKey = null;
    for (String key : modelRegions.keySet()) {
      if (providerModel.startsWith(key) && (bestKey == null || key.length() > bestKey.length())) {
        bestKey = key;
      }
    }
    return bestKey == null ? null : modelRegions.get(bestKey);
  }
}

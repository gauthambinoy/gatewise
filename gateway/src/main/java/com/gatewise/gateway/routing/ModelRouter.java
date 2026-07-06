package com.gatewise.gateway.routing;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Resolves a client-facing model alias to the real provider model the gateway will call.
 *
 * <p>One alias changes the entire upstream target, and only configured aliases are accepted — so
 * the routing table is also the model allow-list. When smart routing is enabled, an alias may
 * instead map to a pool of candidate models and a {@link RoutingStrategy} picks one per request.
 */
@Component
public class ModelRouter {

  private final Map<String, String> aliasToProviderModel;
  private final SmartRoutingProperties smart;
  private final RoutingStrategy strategy;

  public ModelRouter(
      ModelRoutingProperties properties, SmartRoutingProperties smart, RoutingStrategy strategy) {
    this.aliasToProviderModel = Map.copyOf(properties.models());
    this.smart = smart;
    this.strategy = strategy;
  }

  /**
   * Resolves an alias for a specific request: if smart routing is on and the alias has a candidate
   * pool, the strategy chooses; otherwise it falls back to the static 1:1 table.
   */
  public String resolve(String alias, RoutingContext context) {
    if (smart.enabled()) {
      List<String> pool = smart.pools().get(alias);
      if (pool != null && !pool.isEmpty()) {
        return strategy.select(pool, context);
      }
    }
    return resolve(alias);
  }

  /** Returns the provider model for an alias from the static table, or throws if not allowed. */
  public String resolve(String alias) {
    String providerModel = aliasToProviderModel.get(alias);
    if (providerModel == null) {
      throw new ModelNotAllowedException(alias, allowedAliases());
    }
    return providerModel;
  }

  /** The set of aliases this gateway will accept — the static table plus any smart pools. */
  public Set<String> allowedAliases() {
    if (!smart.enabled() || smart.pools().isEmpty()) {
      return aliasToProviderModel.keySet();
    }
    Set<String> all = new HashSet<>(aliasToProviderModel.keySet());
    all.addAll(smart.pools().keySet());
    return all;
  }
}

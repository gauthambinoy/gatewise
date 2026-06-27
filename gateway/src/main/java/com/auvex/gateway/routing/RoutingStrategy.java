package com.auvex.gateway.routing;

import java.util.List;

/**
 * Chooses which provider model to call from a pool of approved candidates for one request.
 *
 * <p>The default {@link CostAwareRoutingStrategy} minimises estimated cost; an operator can supply
 * a different bean to optimise for latency, quality or anything else without touching the hot path.
 */
public interface RoutingStrategy {

  /** Picks one model from {@code candidates} (never empty) for the given request context. */
  String select(List<String> candidates, RoutingContext context);
}

package com.auvex.gateway.routing;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Optional smart-routing pools: an alias maps to several candidate provider models and a {@link
 * RoutingStrategy} picks one per request. Off by default; when disabled the gateway keeps using the
 * strict 1:1 {@link ModelRoutingProperties} table.
 *
 * @param enabled whether smart routing is active
 * @param pools alias → candidate provider models; the listed order is the strategy's tie-break
 */
@ConfigurationProperties(prefix = "auvex.routing.smart")
public record SmartRoutingProperties(boolean enabled, Map<String, List<String>> pools) {

  public SmartRoutingProperties {
    pools = pools == null ? Map.of() : Map.copyOf(pools);
  }
}

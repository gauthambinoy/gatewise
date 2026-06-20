package com.auvex.gateway.routing;

import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Resolves a client-facing model alias to the real provider model the gateway will call.
 *
 * <p>One alias changes the entire upstream target, and only configured aliases are accepted — so
 * the routing table is also the model allow-list.
 */
@Component
public class ModelRouter {

  private final Map<String, String> aliasToProviderModel;

  public ModelRouter(ModelRoutingProperties properties) {
    this.aliasToProviderModel = Map.copyOf(properties.models());
  }

  /** Returns the provider model for an alias, or throws if the alias isn't allowed. */
  public String resolve(String alias) {
    String providerModel = aliasToProviderModel.get(alias);
    if (providerModel == null) {
      throw new ModelNotAllowedException(alias, allowedAliases());
    }
    return providerModel;
  }

  /** The set of aliases this gateway will accept. */
  public Set<String> allowedAliases() {
    return aliasToProviderModel.keySet();
  }
}

package com.gatewise.gateway.routing;

import java.util.Set;
import java.util.TreeSet;

/** Raised when a request asks for a model alias the gateway isn't configured to allow. */
public class ModelNotAllowedException extends RuntimeException {

  public ModelNotAllowedException(String requested, Set<String> allowedAliases) {
    super(
        "Model '"
            + requested
            + "' is not allowed. Allowed models: "
            + new TreeSet<>(allowedAliases)
            + ".");
  }
}

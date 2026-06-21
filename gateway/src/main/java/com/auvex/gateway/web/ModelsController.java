package com.auvex.gateway.web;

import com.auvex.gateway.routing.ModelRoutingProperties;
import java.util.Comparator;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes the model routing table (alias → provider model), which is also the model allow-list. */
@RestController
@RequestMapping("/v1")
public class ModelsController {

  private final ModelRoutingProperties routing;

  public ModelsController(ModelRoutingProperties routing) {
    this.routing = routing;
  }

  /** The configured aliases and what each routes to. */
  @GetMapping("/models")
  public List<RouteView> models() {
    return routing.models().entrySet().stream()
        .map(entry -> new RouteView(entry.getKey(), entry.getValue()))
        .sorted(Comparator.comparing(RouteView::alias))
        .toList();
  }

  /** One routing entry. */
  public record RouteView(String alias, String target) {}
}

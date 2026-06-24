package com.auvex.gateway.web;

import com.auvex.gateway.audit.AuditLogRepository;
import com.auvex.gateway.audit.AuditLogRepository.ModelCount;
import com.auvex.gateway.auth.TenantContext;
import com.auvex.gateway.routing.ModelRoutingProperties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Shadow-AI discovery: surfaces which models and providers a tenant is actually calling, and flags
 * any model that isn't in the configured routing allow-list as "shadow" (un-sanctioned) usage.
 *
 * <p>Because every call is audited, the audit log is a ground-truth inventory of real AI usage —
 * this report turns it into the security question "what AI are people using here, and is any of it
 * off the books?" without a separate agent or network scan.
 */
@RestController
@RequestMapping("/v1")
public class DiscoveryController {

  private final AuditLogRepository audit;
  private final ModelRoutingProperties routing;

  public DiscoveryController(AuditLogRepository audit, ModelRoutingProperties routing) {
    this.audit = audit;
    this.routing = routing;
  }

  /** A breakdown of observed AI usage by provider, with shadow (un-sanctioned) models flagged. */
  @GetMapping("/discovery")
  public DiscoveryReport discover() {
    UUID tenantId = TenantContext.require().tenantId();
    Set<String> sanctioned = Set.copyOf(routing.models().values());

    Map<String, ProviderAccumulator> byProvider = new LinkedHashMap<>();
    List<ModelUsage> shadow = new ArrayList<>();
    int distinctModels = 0;

    for (ModelCount row : audit.countByModel(tenantId)) {
      String model = row.getModel();
      if (model == null || model.isBlank()) {
        continue;
      }
      distinctModels++;
      String provider = providerOf(model);
      boolean isSanctioned = sanctioned.contains(model);
      ModelUsage usage = new ModelUsage(model, provider, row.getCount(), isSanctioned);

      byProvider
          .computeIfAbsent(provider, ProviderAccumulator::new)
          .add(usage);
      if (!isSanctioned) {
        shadow.add(usage);
      }
    }

    List<ProviderUsage> providers =
        byProvider.values().stream()
            .map(ProviderAccumulator::toView)
            .sorted(Comparator.comparingLong(ProviderUsage::requests).reversed())
            .toList();
    shadow.sort(Comparator.comparingLong(ModelUsage::requests).reversed());

    return new DiscoveryReport(distinctModels, shadow.size(), providers, shadow);
  }

  // The provider is the segment before the first '/', e.g. "anthropic/claude-…" → "anthropic".
  private static String providerOf(String model) {
    int slash = model.indexOf('/');
    return slash > 0 ? model.substring(0, slash) : "unknown";
  }

  /** The discovery report. */
  public record DiscoveryReport(
      int distinctModels,
      int shadowModelCount,
      List<ProviderUsage> providers,
      List<ModelUsage> shadowModels) {}

  /** One provider's observed usage. */
  public record ProviderUsage(
      String provider, long requests, boolean fullySanctioned, List<ModelUsage> models) {}

  /** One model's observed usage and whether it's in the allow-list. */
  public record ModelUsage(String model, String provider, long requests, boolean sanctioned) {}

  // Mutable accumulator while folding the per-model rows into per-provider groups.
  private static final class ProviderAccumulator {
    private final String provider;
    private final List<ModelUsage> models = new ArrayList<>();
    private long requests;

    ProviderAccumulator(String provider) {
      this.provider = provider;
    }

    void add(ModelUsage usage) {
      models.add(usage);
      requests += usage.requests();
    }

    ProviderUsage toView() {
      boolean fullySanctioned = models.stream().allMatch(ModelUsage::sanctioned);
      return new ProviderUsage(provider, requests, fullySanctioned, List.copyOf(models));
    }
  }
}

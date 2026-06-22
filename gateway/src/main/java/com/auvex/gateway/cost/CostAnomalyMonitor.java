package com.auvex.gateway.cost;

import com.auvex.gateway.audit.AuditLogRepository;
import com.auvex.gateway.audit.AuditRecordedEvent;
import com.auvex.gateway.config.CostAnomalyProperties;
import com.auvex.gateway.config.WebhookProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Watches each tenant's cumulative spend and raises one alert when it first crosses the configured
 * threshold — logged, and posted to the webhook if one is set. Runs off the request thread and
 * fail-open. Active only when the monitor is enabled.
 */
@Component
@ConditionalOnProperty(name = "auvex.cost-alert.enabled", havingValue = "true")
public class CostAnomalyMonitor {

  private static final Logger LOG = LoggerFactory.getLogger(CostAnomalyMonitor.class);

  private final AuditLogRepository audit;
  private final CostAnomalyProperties properties;
  private final WebhookProperties webhook;
  private final ObjectMapper json;
  private final RestClient http = RestClient.create();
  private final Set<UUID> alerted = ConcurrentHashMap.newKeySet();

  public CostAnomalyMonitor(
      AuditLogRepository audit,
      CostAnomalyProperties properties,
      WebhookProperties webhook,
      ObjectMapper json) {
    this.audit = audit;
    this.properties = properties;
    this.webhook = webhook;
    this.json = json;
  }

  @Async
  @EventListener
  public void onAuditRecorded(AuditRecordedEvent event) {
    UUID tenantId = event.entry().getTenantId();
    BigDecimal spend = audit.sumCostByTenantId(tenantId);
    if (spend.compareTo(properties.thresholdUsd()) <= 0) {
      return;
    }
    if (!alerted.add(tenantId)) {
      return; // already alerted this tenant in this process
    }
    LOG.warn(
        "Cost anomaly: tenant {} spend ${} exceeds threshold ${}",
        tenantId,
        spend,
        properties.thresholdUsd());
    notifyWebhook(tenantId, spend);
  }

  private void notifyWebhook(UUID tenantId, BigDecimal spend) {
    if (webhook.url() == null || webhook.url().isBlank()) {
      return;
    }
    Map<String, Object> alert = new LinkedHashMap<>();
    alert.put("type", "cost_anomaly");
    alert.put("tenantId", tenantId.toString());
    alert.put("spendUsd", spend);
    alert.put("thresholdUsd", properties.thresholdUsd());
    try {
      http.post()
          .uri(webhook.url())
          .contentType(MediaType.APPLICATION_JSON)
          .body(json.writeValueAsString(alert))
          .retrieve()
          .toBodilessEntity();
    } catch (JsonProcessingException | RestClientException e) {
      LOG.warn("Failed to send cost anomaly alert: {}", e.getMessage());
    }
  }
}

package com.gatewise.gateway.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatewise.gateway.audit.AuditRecordedEvent;
import com.gatewise.gateway.config.WebhookProperties;
import com.gatewise.gateway.web.AuditView;
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
 * Forwards each audit event to a configured webhook / SIEM, off the request thread and fail-open: a
 * webhook outage logs a warning but never breaks a gateway call. Active only when the webhook is
 * enabled, so the default path has zero overhead.
 */
@Component
@ConditionalOnProperty(name = "gatewise.webhook.enabled", havingValue = "true")
public class WebhookForwarder {

  private static final Logger LOG = LoggerFactory.getLogger(WebhookForwarder.class);

  private final RestClient http = RestClient.create();
  private final WebhookProperties properties;
  private final ObjectMapper json;

  public WebhookForwarder(WebhookProperties properties, ObjectMapper json) {
    this.properties = properties;
    this.json = json;
  }

  @Async
  @EventListener
  public void onAuditRecorded(AuditRecordedEvent event) {
    if (properties.url() == null || properties.url().isBlank()) {
      return;
    }
    try {
      String payload = json.writeValueAsString(AuditView.of(event.entry()));
      http.post()
          .uri(properties.url())
          .contentType(MediaType.APPLICATION_JSON)
          .body(payload)
          .retrieve()
          .toBodilessEntity();
    } catch (JsonProcessingException | RestClientException e) {
      LOG.warn("Failed to forward audit event to the webhook: {}", e.getMessage());
    }
  }
}

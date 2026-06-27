package com.auvex.gateway.audit;

import com.auvex.gateway.auth.TenantContext;
import com.auvex.gateway.config.AuditTransportProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Async sink: publish the entry to Kafka so a consumer can persist it off the request path. */
@Component
@ConditionalOnProperty(name = "auvex.audit.async", havingValue = "true")
public class KafkaAuditSink implements AuditSink {

  private final KafkaTemplate<String, String> kafka;
  private final ObjectMapper json;
  private final AuditTransportProperties properties;

  public KafkaAuditSink(
      KafkaTemplate<String, String> kafka, ObjectMapper json, AuditTransportProperties properties) {
    this.kafka = kafka;
    this.json = json;
    this.properties = properties;
  }

  @Override
  public void record(AuditEntry entry) {
    // Stamp the principal before publishing — the consumer thread won't have it bound.
    AuditEntry enriched = entry.withPrincipal(TenantContext.get());
    try {
      // Key by tenant so one tenant's entries land on the same partition, preserving order.
      kafka.send(
          properties.topic(), enriched.tenantId().toString(), json.writeValueAsString(enriched));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize audit entry", e);
    }
  }
}

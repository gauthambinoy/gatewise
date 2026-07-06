package com.gatewise.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How audit entries are transported.
 *
 * @param async when true, entries are published to Kafka and persisted by a consumer so the write
 *     never blocks the live call; when false they are written synchronously
 * @param topic the Kafka topic used in async mode
 */
@ConfigurationProperties(prefix = "gatewise.audit")
public record AuditTransportProperties(boolean async, String topic) {

  public AuditTransportProperties {
    if (topic == null || topic.isBlank()) {
      topic = "gatewise.audit";
    }
  }
}

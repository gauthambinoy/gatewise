package com.gatewise.gateway.audit;

import com.gatewise.gateway.config.AuditTransportProperties;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Creates the audit topic when async auditing is enabled. */
@Configuration
@ConditionalOnProperty(name = "gatewise.audit.async", havingValue = "true")
public class KafkaAuditConfig {

  /** A single-partition topic (per-tenant keying keeps a tenant's entries ordered within it). */
  @Bean
  public NewTopic auditTopic(AuditTransportProperties properties) {
    return TopicBuilder.name(properties.topic()).partitions(1).replicas(1).build();
  }
}

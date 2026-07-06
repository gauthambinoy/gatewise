package com.gatewise.gateway.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Consumes published audit entries and persists them via the hash chain, off the request path. */
@Component
@ConditionalOnProperty(name = "gatewise.audit.async", havingValue = "true")
public class KafkaAuditConsumer {

  private final AuditService audit;
  private final ObjectMapper json;

  public KafkaAuditConsumer(AuditService audit, ObjectMapper json) {
    this.audit = audit;
    this.json = json;
  }

  /** Persists one published entry. */
  @KafkaListener(topics = "${gatewise.audit.topic:gatewise.audit}", groupId = "gatewise-audit")
  public void onMessage(String payload) throws JsonProcessingExceptionWrapper {
    try {
      audit.append(json.readValue(payload, AuditEntry.class));
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new JsonProcessingExceptionWrapper(e);
    }
  }

  /** Unchecked wrapper so a malformed message surfaces to Kafka's error handler. */
  static class JsonProcessingExceptionWrapper extends RuntimeException {
    JsonProcessingExceptionWrapper(Throwable cause) {
      super(cause);
    }
  }
}

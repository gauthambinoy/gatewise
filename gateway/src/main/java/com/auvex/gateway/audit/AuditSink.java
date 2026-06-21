package com.auvex.gateway.audit;

/** Where an audit entry goes — straight to the database, or via Kafka. */
public interface AuditSink {

  /** Records the entry (synchronously or asynchronously, depending on the implementation). */
  void record(AuditEntry entry);
}

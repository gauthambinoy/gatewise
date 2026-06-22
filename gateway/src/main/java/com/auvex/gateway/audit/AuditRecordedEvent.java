package com.auvex.gateway.audit;

/**
 * Published after an audit entry is persisted, so listeners (e.g. the webhook forwarder) can react
 * without coupling to the audit write.
 */
public record AuditRecordedEvent(AuditLog entry) {}

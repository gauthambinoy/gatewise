package com.gatewise.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Audit webhook / SIEM streaming.
 *
 * @param enabled forward each audit event to the webhook
 * @param url the destination (e.g. a Splunk HEC, Datadog, or a generic collector)
 */
@ConfigurationProperties(prefix = "gatewise.webhook")
public record WebhookProperties(boolean enabled, String url) {}

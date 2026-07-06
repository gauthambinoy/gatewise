package com.gatewise.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Compliance settings.
 *
 * @param retentionDays how long audit records are kept before retention deletion (also reported in
 *     the compliance report)
 * @param retentionDeleteEnabled actually delete audit entries older than {@code retentionDays} on a
 *     daily schedule (off by default; a hard delete truncates the oldest end of the hash chain —
 *     the remaining chain stays verifiable from its earliest surviving entry)
 * @param notaryUrl optional external timestamping/notary endpoint; when set, the chain anchor from
 *     {@code GET /v1/compliance/notarization} is also POSTed there (fail-open). Off when unset.
 */
@ConfigurationProperties(prefix = "gatewise.compliance")
public record ComplianceProperties(
    Integer retentionDays, boolean retentionDeleteEnabled, String notaryUrl) {

  public ComplianceProperties {
    if (retentionDays == null || retentionDays <= 0) {
      retentionDays = 365;
    }
  }
}

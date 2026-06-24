package com.auvex.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Compliance settings.
 *
 * @param retentionDays how long audit records are retained (reported in the compliance report;
 *     enforcement is by archival, since deleting from the hash chain would break tamper-evidence)
 */
@ConfigurationProperties(prefix = "auvex.compliance")
public record ComplianceProperties(Integer retentionDays) {

  public ComplianceProperties {
    if (retentionDays == null || retentionDays <= 0) {
      retentionDays = 365;
    }
  }
}

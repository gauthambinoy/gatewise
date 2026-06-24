package com.auvex.gateway.audit;

import com.auvex.gateway.config.ComplianceProperties;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Enforces the audit data-retention window by deleting entries older than {@code retentionDays}.
 *
 * <p>Runs once a day when {@code auvex.compliance.retention-delete-enabled} is on (off by default).
 * Deletion is a hard delete: it removes the oldest end of each tenant's hash chain, and the chain
 * stays verifiable from its earliest surviving entry (see {@link AuditService#firstBrokenLink}).
 */
@Service
public class RetentionService {

  private static final Logger LOG = LoggerFactory.getLogger(RetentionService.class);

  private final AuditLogRepository repository;
  private final ComplianceProperties properties;

  public RetentionService(AuditLogRepository repository, ComplianceProperties properties) {
    this.repository = repository;
    this.properties = properties;
  }

  /** Deletes every audit entry created before {@code cutoff}; returns how many were removed. */
  public long purgeOlderThan(Instant cutoff) {
    return repository.deleteByCreatedAtBefore(cutoff.atOffset(ZoneOffset.UTC));
  }

  /** Daily retention sweep — a no-op unless retention deletion is enabled. */
  @Scheduled(cron = "${auvex.compliance.retention-cron:0 0 3 * * *}")
  public void enforceRetention() {
    if (!properties.retentionDeleteEnabled()) {
      return;
    }
    OffsetDateTime cutoff =
        OffsetDateTime.now(ZoneOffset.UTC).minusDays(properties.retentionDays());
    long removed = repository.deleteByCreatedAtBefore(cutoff);
    if (removed > 0) {
      LOG.info("Retention: purged {} audit entries older than {}", removed, cutoff);
    }
  }
}

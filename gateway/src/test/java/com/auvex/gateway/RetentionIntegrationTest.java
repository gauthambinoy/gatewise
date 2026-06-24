package com.auvex.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.auvex.gateway.audit.AuditEntry;
import com.auvex.gateway.audit.AuditLogRepository;
import com.auvex.gateway.audit.AuditService;
import com.auvex.gateway.audit.AuditSink;
import com.auvex.gateway.audit.RetentionService;
import com.auvex.gateway.audit.Verdict;
import com.auvex.gateway.support.AbstractPostgresIntegrationTest;
import com.auvex.gateway.tenant.Tenant;
import com.auvex.gateway.tenant.TenantRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Proves retention deletion removes old entries and leaves the remaining chain verifiable. */
class RetentionIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private RetentionService retention;
  @Autowired private AuditSink auditSink;
  @Autowired private AuditService auditService;
  @Autowired private AuditLogRepository auditLog;
  @Autowired private TenantRepository tenants;

  private UUID tenant() {
    return tenants.save(new Tenant("Acme", "acme-" + UUID.randomUUID())).getId();
  }

  @Test
  void purgesEntriesOlderThanCutoff() {
    UUID t = tenant();
    record(t);
    record(t);
    assertThat(auditLog.findByTenantIdOrderByIdAsc(t)).hasSize(2);

    // A cutoff just after now removes everything created so far.
    long removed = retention.purgeOlderThan(Instant.now().plus(1, ChronoUnit.MINUTES));
    assertThat(removed).isGreaterThanOrEqualTo(2);
    assertThat(auditLog.findByTenantIdOrderByIdAsc(t)).isEmpty();
  }

  @Test
  void keepsRecentEntriesAndChainStaysVerifiableAfterTruncation() {
    UUID t = tenant();
    record(t); // will be purged
    record(t); // survives
    record(t); // survives

    // Purge only the first entry by id, simulating retention truncating the oldest prefix.
    Long firstId = auditLog.findByTenantIdOrderByIdAsc(t).get(0).getId();
    auditLog.deleteById(firstId);

    // The remaining two are intact and contiguous, so verification anchors on the new earliest.
    assertThat(auditLog.findByTenantIdOrderByIdAsc(t)).hasSize(2);
    assertThat(auditService.firstBrokenLink(t)).isEmpty();
  }

  private void record(UUID tenantId) {
    auditSink.record(
        new AuditEntry(
            tenantId,
            UUID.randomUUID(),
            "alice",
            "default",
            Verdict.ALLOWED,
            "hello",
            null,
            Instant.now()));
  }
}

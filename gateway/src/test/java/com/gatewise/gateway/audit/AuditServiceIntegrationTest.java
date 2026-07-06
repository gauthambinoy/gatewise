package com.gatewise.gateway.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.gatewise.gateway.support.AbstractPostgresIntegrationTest;
import com.gatewise.gateway.tenant.Tenant;
import com.gatewise.gateway.tenant.TenantRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Verifies the audit log persists a hash chain and detects tampering, against a real Postgres. */
class AuditServiceIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private AuditService audit;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TenantRepository tenants;

  // audit_log.tenant_id is a foreign key, so the tenant must really exist.
  private UUID newTenantId() {
    return tenants.save(new Tenant("Acme", "acme-" + UUID.randomUUID())).getId();
  }

  private static AuditEntry entry(UUID tenant, String prompt, Verdict verdict) {
    return new AuditEntry(
        tenant, UUID.randomUUID(), "svc", "model-x", verdict, prompt, null, Instant.now());
  }

  @Test
  void appendsAnEntryStartingFromGenesis() { // T26
    UUID tenant = newTenantId();
    AuditLog row = audit.append(entry(tenant, "hello", Verdict.ALLOWED));
    assertThat(row.getId()).isNotNull();
    assertThat(row.getEntryHash()).hasSize(64).matches("[0-9a-f]{64}");
    assertThat(row.getPrevHash()).isEqualTo(AuditChain.GENESIS);
  }

  @Test
  void anIntactChainVerifies() { // T27
    UUID tenant = newTenantId();
    audit.append(entry(tenant, "one", Verdict.ALLOWED));
    audit.append(entry(tenant, "two", Verdict.REDACTED));
    audit.append(entry(tenant, "three", Verdict.BLOCKED));
    assertThat(audit.firstBrokenLink(tenant)).isEmpty();
  }

  @Test
  void tamperingWithAStoredFieldBreaksTheChain() { // T27
    UUID tenant = newTenantId();
    audit.append(entry(tenant, "one", Verdict.ALLOWED));
    AuditLog second = audit.append(entry(tenant, "two", Verdict.ALLOWED));

    // Tamper: change a stored field without recomputing its hash.
    jdbc.update("UPDATE audit_log SET verdict = 'blocked' WHERE id = ?", second.getId());

    assertThat(audit.firstBrokenLink(tenant)).contains(second.getId());
  }
}

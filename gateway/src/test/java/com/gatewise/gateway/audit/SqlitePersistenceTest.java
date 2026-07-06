package com.gatewise.gateway.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.gatewise.gateway.tenant.Tenant;
import com.gatewise.gateway.tenant.TenantRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Proves the persistence core — schema creation, the entities, the repositories and the audit hash
 * chain — runs on the embedded SQLite backend (the local single-binary {@code sqlite} profile),
 * with the no-op {@link SqliteChainLock} standing in for the Postgres advisory lock and Hibernate
 * (not Flyway) creating the schema via the community SQLite dialect.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("sqlite")
@Import({AuditService.class, SqliteChainLock.class})
class SqlitePersistenceTest {

  @DynamicPropertySource
  static void sqliteFile(DynamicPropertyRegistry registry) {
    // A throwaway on-disk SQLite database, so we exercise the real file-backed driver and dialect.
    String file =
        System.getProperty("java.io.tmpdir") + "/gatewise-sqlite-" + UUID.randomUUID() + ".db";
    registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + file);
  }

  @Autowired private TenantRepository tenants;
  @Autowired private AuditService auditService;
  @Autowired private AuditLogRepository auditLogs;

  @Test
  void appendsAndVerifiesAHashChainOnSqlite() {
    UUID tenantId = tenants.save(new Tenant("Local Co", "local-" + UUID.randomUUID())).getId();

    auditService.append(
        new AuditEntry(
            tenantId,
            UUID.randomUUID(),
            "alice",
            "openai/gpt-4o",
            Verdict.ALLOWED,
            "hello",
            null,
            Instant.now()));
    auditService.append(
        new AuditEntry(
            tenantId,
            UUID.randomUUID(),
            "alice",
            "openai/gpt-4o",
            Verdict.REDACTED,
            "[EMAIL]",
            null,
            Instant.now()));

    // Both entries persisted, and the per-tenant hash chain they form verifies intact on SQLite.
    assertThat(auditLogs.findByTenantIdOrderByIdAsc(tenantId)).hasSize(2);
    assertThat(auditService.firstBrokenLink(tenantId)).isEmpty();
  }
}

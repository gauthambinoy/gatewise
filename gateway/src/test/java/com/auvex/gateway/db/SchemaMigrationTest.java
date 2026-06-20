package com.auvex.gateway.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.auvex.gateway.support.AbstractPostgresIntegrationTest;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifies the Flyway migration actually applies on a real Postgres and produces the schema our ER
 * diagram documents (docs/ARCHITECTURE.md). This pins the schema so an accidental change to a
 * migration is caught immediately.
 */
class SchemaMigrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private JdbcTemplate jdbc;

  /** T07 — Flyway recorded at least one successful migration. */
  @Test
  void flywayAppliedTheBaselineMigration() {
    Integer applied =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE", Integer.class);
    assertThat(applied).isNotNull().isPositive();
  }

  /** T08 — the four core tables exist with the columns the ER diagram shows. */
  @Test
  void coreTablesExistWithExpectedColumns() {
    assertThat(tableNames()).contains("tenant", "api_key", "policy", "audit_log");

    assertThat(columnsOf("tenant")).contains("id", "name", "slug", "status", "created_at");
    assertThat(columnsOf("api_key")).contains("tenant_id", "key_hash", "prefix", "status");
    assertThat(columnsOf("policy"))
        .contains("tenant_id", "effect", "resource_type", "resource_value", "priority");
    assertThat(columnsOf("audit_log"))
        .contains("tenant_id", "request_id", "verdict", "prev_hash", "entry_hash");
  }

  private Set<String> tableNames() {
    return new HashSet<>(
        jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
            String.class));
  }

  private Set<String> columnsOf(String table) {
    return new HashSet<>(
        jdbc.queryForList(
            "SELECT column_name FROM information_schema.columns"
                + " WHERE table_schema = 'public' AND table_name = ?",
            String.class,
            table));
  }
}

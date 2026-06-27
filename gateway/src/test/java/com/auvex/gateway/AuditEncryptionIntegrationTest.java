package com.auvex.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.auvex.gateway.audit.AuditEntry;
import com.auvex.gateway.audit.AuditLog;
import com.auvex.gateway.audit.AuditLogRepository;
import com.auvex.gateway.audit.AuditService;
import com.auvex.gateway.audit.Verdict;
import com.auvex.gateway.support.AbstractPostgresIntegrationTest;
import com.auvex.gateway.tenant.Tenant;
import com.auvex.gateway.tenant.TenantRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Proves field-level encryption end to end with it actually switched ON: the redacted prompt is
 * stored as ciphertext, reads return the original plaintext, and the hash chain still verifies and
 * still detects tampering.
 */
class AuditEncryptionIntegrationTest extends AbstractPostgresIntegrationTest {

  // A real 32-byte AES-256 key, base64-encoded.
  private static final String TEST_KEY =
      Base64.getEncoder()
          .encodeToString("01234567890123456789012345678901".getBytes(StandardCharsets.US_ASCII));

  @DynamicPropertySource
  static void encryptionProperties(DynamicPropertyRegistry registry) {
    registry.add("auvex.encryption.enabled", () -> "true");
    registry.add("auvex.encryption.key", () -> TEST_KEY);
  }

  @Autowired private AuditService audit;
  @Autowired private AuditLogRepository repository;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TenantRepository tenants;

  private UUID newTenantId() {
    return tenants.save(new Tenant("Acme", "acme-" + UUID.randomUUID())).getId();
  }

  private AuditLog append(UUID tenant, String prompt) {
    return audit.append(
        new AuditEntry(
            tenant,
            UUID.randomUUID(),
            "svc",
            "model-x",
            Verdict.ALLOWED,
            prompt,
            null,
            Instant.now()));
  }

  @Test
  void storesCiphertextButReadsBackPlaintextAndKeepsTheChainVerifiable() {
    UUID tenant = newTenantId();
    String prompt = "transfer the funds to account 12345678";

    AuditLog saved = append(tenant, prompt);

    // Read the raw column straight from the DB, bypassing the JPA converter: it must be ciphertext.
    String rawColumn =
        jdbc.queryForObject(
            "SELECT prompt_redacted FROM audit_log WHERE id = ?", String.class, saved.getId());
    assertThat(rawColumn).startsWith("encv1:").isNotEqualTo(prompt);

    // A fresh load through the repository runs the converter and decrypts back to the original.
    AuditLog reloaded = repository.findById(saved.getId()).orElseThrow();
    assertThat(reloaded.getPromptRedacted()).isEqualTo(prompt);

    // The chain is computed over plaintext, so verification must still pass with encryption on.
    assertThat(audit.firstBrokenLink(tenant)).isEmpty();
  }

  @Test
  void tamperingWithTheEncryptedColumnStillBreaksTheChain() {
    UUID tenant = newTenantId();
    append(tenant, "one");
    AuditLog second = append(tenant, "two");

    // Overwrite the stored prompt with a different value; on read it's unmarked plaintext, so the
    // recomputed hash no longer matches the stored one and the chain breaks at this entry.
    jdbc.update(
        "UPDATE audit_log SET prompt_redacted = ? WHERE id = ?", "tampered", second.getId());

    assertThat(audit.firstBrokenLink(tenant)).contains(second.getId());
  }
}

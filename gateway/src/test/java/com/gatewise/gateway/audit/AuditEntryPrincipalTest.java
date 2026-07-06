package com.gatewise.gateway.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.gatewise.gateway.auth.AuthenticatedTenant;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for stamping the acting principal onto an audit entry. */
class AuditEntryPrincipalTest {

  private AuditEntry base() {
    return new AuditEntry(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "user-1",
        "model-x",
        Verdict.ALLOWED,
        "hello",
        null,
        Instant.parse("2026-01-01T00:00:00Z"),
        1,
        1,
        BigDecimal.ZERO,
        Map.of());
  }

  @Test
  void stampsApiKeyPrincipal() {
    UUID keyId = UUID.randomUUID();
    AuditEntry stamped = base().withPrincipal(new AuthenticatedTenant(UUID.randomUUID(), keyId));

    assertThat(stamped.principalType()).isEqualTo("api_key");
    assertThat(stamped.principalId()).isEqualTo(keyId);
    assertThat(stamped.principalEmail()).isNull();
  }

  @Test
  void stampsMemberPrincipalWithEmail() {
    UUID memberId = UUID.randomUUID();
    AuditEntry stamped =
        base().withPrincipal(new AuthenticatedTenant(UUID.randomUUID(), null, memberId, "m@x.io"));

    assertThat(stamped.principalType()).isEqualTo("member");
    assertThat(stamped.principalId()).isEqualTo(memberId);
    assertThat(stamped.principalEmail()).isEqualTo("m@x.io");
  }

  @Test
  void nullPrincipalLeavesTheEntryUntouched() {
    AuditEntry entry = base();
    assertThat(entry.withPrincipal(null)).isSameAs(entry);
  }

  @Test
  void doesNotOverwriteAnAlreadyStampedPrincipal() {
    UUID firstKey = UUID.randomUUID();
    AuditEntry first = base().withPrincipal(new AuthenticatedTenant(UUID.randomUUID(), firstKey));
    AuditEntry again =
        first.withPrincipal(new AuthenticatedTenant(UUID.randomUUID(), UUID.randomUUID()));

    assertThat(again).isSameAs(first);
    assertThat(again.principalId()).isEqualTo(firstKey);
  }
}

package com.auvex.gateway.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.auvex.gateway.auth.AuthenticatedTenant;
import com.auvex.gateway.auth.TenantContext;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Proves the sink stamps the bound principal onto the entry before it is persisted. */
class SyncAuditSinkPrincipalTest {

  private final AuditService audit = mock(AuditService.class);
  private final SyncAuditSink sink = new SyncAuditSink(audit);

  @AfterEach
  void clearContext() {
    TenantContext.clear();
  }

  @Test
  void enrichesEntryWithTheBoundMemberPrincipal() {
    UUID tenant = UUID.randomUUID();
    UUID memberId = UUID.randomUUID();
    TenantContext.set(new AuthenticatedTenant(tenant, null, memberId, "ops@corp.com"));

    sink.record(
        new AuditEntry(
            tenant,
            UUID.randomUUID(),
            "ops@corp.com",
            "model-x",
            Verdict.ALLOWED,
            "hi",
            null,
            Instant.now()));

    ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
    verify(audit).append(captor.capture());
    assertThat(captor.getValue().principalType()).isEqualTo("member");
    assertThat(captor.getValue().principalId()).isEqualTo(memberId);
    assertThat(captor.getValue().principalEmail()).isEqualTo("ops@corp.com");
  }

  @Test
  void leavesPrincipalEmptyWhenNothingIsBound() {
    sink.record(
        new AuditEntry(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "anon",
            "model-x",
            Verdict.ALLOWED,
            "hi",
            null,
            Instant.now()));

    ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
    verify(audit).append(captor.capture());
    assertThat(captor.getValue().principalType()).isNull();
    assertThat(captor.getValue().principalId()).isNull();
  }
}

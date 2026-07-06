package com.gatewise.gateway.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.gatewise.gateway.auth.AuthenticatedTenant;
import com.gatewise.gateway.auth.TenantContext;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Unit tests for recording management actions against the bound principal, fail-open. */
class ManagementAuditServiceTest {

  private final ManagementAuditRepository repository = mock(ManagementAuditRepository.class);
  private final ManagementAuditService service = new ManagementAuditService(repository);

  @AfterEach
  void clearContext() {
    TenantContext.clear();
  }

  @Test
  void recordsTheActionAgainstTheBoundPrincipal() {
    UUID tenant = UUID.randomUUID();
    UUID memberId = UUID.randomUUID();
    UUID resourceId = UUID.randomUUID();
    TenantContext.set(new AuthenticatedTenant(tenant, null, memberId, "admin@corp.com"));

    service.record("key.revoke", "api_key", resourceId, "rotated");

    ArgumentCaptor<ManagementAudit> captor = ArgumentCaptor.forClass(ManagementAudit.class);
    verify(repository).save(captor.capture());
    ManagementAudit saved = captor.getValue();
    assertThat(saved.getTenantId()).isEqualTo(tenant);
    assertThat(saved.getPrincipalType()).isEqualTo("member");
    assertThat(saved.getPrincipalId()).isEqualTo(memberId);
    assertThat(saved.getPrincipalEmail()).isEqualTo("admin@corp.com");
    assertThat(saved.getAction()).isEqualTo("key.revoke");
    assertThat(saved.getResourceType()).isEqualTo("api_key");
    assertThat(saved.getResourceId()).isEqualTo(resourceId);
    assertThat(saved.getDetail()).isEqualTo("rotated");
  }

  @Test
  void failsOpenWhenThePersistThrows() {
    TenantContext.set(new AuthenticatedTenant(UUID.randomUUID(), UUID.randomUUID()));
    doThrow(new RuntimeException("db down")).when(repository).save(any());

    assertThatCode(() -> service.record("key.create", "api_key", UUID.randomUUID(), null))
        .doesNotThrowAnyException();
  }
}

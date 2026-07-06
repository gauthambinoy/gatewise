package com.gatewise.gateway.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for how a principal is derived from the authenticated tenant. */
class AuthenticatedTenantTest {

  @Test
  void apiKeyPrincipalReportsApiKeyTypeAndId() {
    UUID keyId = UUID.randomUUID();
    AuthenticatedTenant tenant = new AuthenticatedTenant(UUID.randomUUID(), keyId);

    assertThat(tenant.principalType()).isEqualTo("api_key");
    assertThat(tenant.principalId()).isEqualTo(keyId);
    assertThat(tenant.memberEmail()).isNull();
  }

  @Test
  void memberPrincipalWinsAndCarriesTheEmail() {
    UUID memberId = UUID.randomUUID();
    AuthenticatedTenant tenant =
        new AuthenticatedTenant(UUID.randomUUID(), null, memberId, "alice@corp.com");

    assertThat(tenant.principalType()).isEqualTo("member");
    assertThat(tenant.principalId()).isEqualTo(memberId);
    assertThat(tenant.memberEmail()).isEqualTo("alice@corp.com");
  }

  @Test
  void noIdentityFallsBackToSystem() {
    AuthenticatedTenant tenant = new AuthenticatedTenant(UUID.randomUUID(), null, null, null);

    assertThat(tenant.principalType()).isEqualTo("system");
    assertThat(tenant.principalId()).isNull();
  }
}

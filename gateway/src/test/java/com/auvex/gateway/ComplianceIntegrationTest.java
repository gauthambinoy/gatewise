package com.auvex.gateway;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.auvex.gateway.audit.AuditEntry;
import com.auvex.gateway.audit.AuditSink;
import com.auvex.gateway.audit.Verdict;
import com.auvex.gateway.auth.ApiKeyHasher;
import com.auvex.gateway.support.AbstractPostgresIntegrationTest;
import com.auvex.gateway.tenant.ApiKey;
import com.auvex.gateway.tenant.ApiKeyRepository;
import com.auvex.gateway.tenant.Tenant;
import com.auvex.gateway.tenant.TenantRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Proves the compliance report aggregates real audit data and reports control status. */
@AutoConfigureMockMvc
class ComplianceIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantRepository tenants;
  @Autowired private ApiKeyRepository apiKeys;
  @Autowired private AuditSink auditSink;

  @Test
  void reportsActivityControlsAndChainIntegrity() throws Exception {
    Tenant tenant = tenants.save(new Tenant("Acme", "acme-" + UUID.randomUUID()));
    String raw = "auvex_" + UUID.randomUUID().toString().replace("-", "");
    apiKeys.save(
        new ApiKey(tenant.getId(), "default", ApiKeyHasher.hash(raw), raw.substring(0, 12)));

    auditSink.record(
        new AuditEntry(
            tenant.getId(),
            UUID.randomUUID(),
            "a",
            "default",
            Verdict.ALLOWED,
            "hi",
            null,
            Instant.now()));
    auditSink.record(
        new AuditEntry(
            tenant.getId(),
            UUID.randomUUID(),
            "a",
            "default",
            Verdict.REDACTED,
            "masked",
            null,
            Instant.now(),
            1,
            1,
            null,
            Map.of("email", 2)));

    mvc.perform(get("/v1/compliance/report").header("Authorization", "Bearer " + raw))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalCalls").value(2))
        .andExpect(jsonPath("$.callsByVerdict.allowed").value(1))
        .andExpect(jsonPath("$.callsByVerdict.redacted").value(1))
        .andExpect(jsonPath("$.piiItemsMasked").value(2))
        .andExpect(jsonPath("$.piiMaskedByType.email").value(2))
        .andExpect(jsonPath("$.auditChainIntact").value(true))
        .andExpect(jsonPath("$.retentionDays").value(365))
        .andExpect(jsonPath("$.controls[0].framework").exists());
  }
}

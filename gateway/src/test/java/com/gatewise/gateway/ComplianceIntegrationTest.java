package com.gatewise.gateway;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gatewise.gateway.audit.AuditEntry;
import com.gatewise.gateway.audit.AuditLog;
import com.gatewise.gateway.audit.AuditService;
import com.gatewise.gateway.audit.AuditSink;
import com.gatewise.gateway.audit.Verdict;
import com.gatewise.gateway.auth.ApiKeyHasher;
import com.gatewise.gateway.support.AbstractPostgresIntegrationTest;
import com.gatewise.gateway.tenant.ApiKey;
import com.gatewise.gateway.tenant.ApiKeyRepository;
import com.gatewise.gateway.tenant.Tenant;
import com.gatewise.gateway.tenant.TenantRepository;
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
  @Autowired private AuditService audit;

  @Test
  void reportsActivityControlsAndChainIntegrity() throws Exception {
    Tenant tenant = tenants.save(new Tenant("Acme", "acme-" + UUID.randomUUID()));
    String raw = "gatewise_" + UUID.randomUUID().toString().replace("-", "");
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

  @Test
  void notarizationReturnsTheChainHeadForTheTenant() throws Exception {
    Tenant tenant = tenants.save(new Tenant("Acme", "acme-" + UUID.randomUUID()));
    String raw = "gatewise_" + UUID.randomUUID().toString().replace("-", "");
    apiKeys.save(
        new ApiKey(tenant.getId(), "default", ApiKeyHasher.hash(raw), raw.substring(0, 12)));

    audit.append(
        new AuditEntry(
            tenant.getId(),
            UUID.randomUUID(),
            "a",
            "model-x",
            Verdict.ALLOWED,
            "one",
            null,
            Instant.now()));
    AuditLog head =
        audit.append(
            new AuditEntry(
                tenant.getId(),
                UUID.randomUUID(),
                "a",
                "model-x",
                Verdict.ALLOWED,
                "two",
                null,
                Instant.now()));

    mvc.perform(get("/v1/compliance/notarization").header("Authorization", "Bearer " + raw))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenantId").value(tenant.getId().toString()))
        .andExpect(jsonPath("$.headEntryId").value(head.getId().intValue()))
        .andExpect(jsonPath("$.headHash").value(head.getEntryHash()))
        .andExpect(jsonPath("$.chainIntact").value(true))
        .andExpect(jsonPath("$.generatedAt").exists());
  }
}

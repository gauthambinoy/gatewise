package com.gatewise.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gatewise.gateway.audit.AuditEntry;
import com.gatewise.gateway.audit.AuditLogRepository;
import com.gatewise.gateway.audit.AuditService;
import com.gatewise.gateway.audit.RetentionService;
import com.gatewise.gateway.audit.Verdict;
import com.gatewise.gateway.auth.ApiKeyHasher;
import com.gatewise.gateway.support.AbstractPostgresIntegrationTest;
import com.gatewise.gateway.tenant.ApiKey;
import com.gatewise.gateway.tenant.ApiKeyRepository;
import com.gatewise.gateway.tenant.Tenant;
import com.gatewise.gateway.tenant.TenantRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Proves a legal hold exempts a data subject's audit entries from retention deletion. */
@AutoConfigureMockMvc
class LegalHoldIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private AuditService audit;
  @Autowired private RetentionService retention;
  @Autowired private AuditLogRepository entries;
  @Autowired private TenantRepository tenants;
  @Autowired private ApiKeyRepository apiKeys;

  private record TenantAuth(UUID tenantId, String key) {}

  private TenantAuth newTenant() {
    Tenant t = tenants.save(new Tenant("Acme", "acme-" + UUID.randomUUID()));
    String raw = "gatewise_" + UUID.randomUUID().toString().replace("-", "");
    apiKeys.save(new ApiKey(t.getId(), "default", ApiKeyHasher.hash(raw), raw.substring(0, 12)));
    return new TenantAuth(t.getId(), raw);
  }

  // An entry old enough to fall outside any retention window.
  private void appendOld(UUID tenantId, String actor) {
    audit.append(
        new AuditEntry(
            tenantId,
            UUID.randomUUID(),
            actor,
            "model-x",
            Verdict.ALLOWED,
            "prompt",
            null,
            Instant.now().minusSeconds(3600)));
  }

  @Test
  void heldSubjectSurvivesThePurgeWhileOthersAreDeleted() throws Exception {
    TenantAuth a = newTenant();
    appendOld(a.tenantId(), "alice");
    appendOld(a.tenantId(), "alice");
    appendOld(a.tenantId(), "bob");

    mvc.perform(
            post("/v1/compliance/legal-hold")
                .header("Authorization", "Bearer " + a.key())
                .contentType("application/json")
                .content("{\"subject\":\"alice\",\"hold\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.affected").value(2))
        .andExpect(jsonPath("$.hold").value(true));

    retention.purgeOlderThan(Instant.now());

    var remaining = entries.findByTenantIdOrderByIdAsc(a.tenantId());
    assertThat(remaining).hasSize(2);
    assertThat(remaining).allMatch(e -> "alice".equals(e.getActor()));
    assertThat(remaining).allMatch(com.gatewise.gateway.audit.AuditLog::isLegalHold);
  }

  @Test
  void releasingTheHoldLetsEntriesBePurgedAgain() throws Exception {
    TenantAuth a = newTenant();
    appendOld(a.tenantId(), "carol");

    place(a, "carol", true);
    mvc.perform(
            post("/v1/compliance/legal-hold")
                .header("Authorization", "Bearer " + a.key())
                .contentType("application/json")
                .content("{\"subject\":\"carol\",\"hold\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.affected").value(1))
        .andExpect(jsonPath("$.hold").value(false));

    retention.purgeOlderThan(Instant.now());

    assertThat(entries.findByTenantIdOrderByIdAsc(a.tenantId())).isEmpty();
  }

  private void place(TenantAuth a, String subject, boolean hold) throws Exception {
    mvc.perform(
            post("/v1/compliance/legal-hold")
                .header("Authorization", "Bearer " + a.key())
                .contentType("application/json")
                .content("{\"subject\":\"" + subject + "\",\"hold\":" + hold + "}"))
        .andExpect(status().isOk());
  }
}

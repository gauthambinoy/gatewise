package com.gatewise.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gatewise.gateway.audit.AuditEntry;
import com.gatewise.gateway.audit.AuditService;
import com.gatewise.gateway.audit.Verdict;
import com.gatewise.gateway.auth.ApiKeyHasher;
import com.gatewise.gateway.support.AbstractPostgresIntegrationTest;
import com.gatewise.gateway.tenant.ApiKey;
import com.gatewise.gateway.tenant.ApiKeyRepository;
import com.gatewise.gateway.tenant.Tenant;
import com.gatewise.gateway.tenant.TenantRepository;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Exercises the cost chargeback/showback report: spend by model and user, plus the projection. */
@AutoConfigureMockMvc
class ChargebackIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private AuditService audit;
  @Autowired private TenantRepository tenants;
  @Autowired private ApiKeyRepository apiKeys;

  private record TenantAuth(UUID tenantId, String key) {}

  private TenantAuth newTenant() {
    Tenant t = tenants.save(new Tenant("Acme", "acme-" + UUID.randomUUID()));
    String raw = "gatewise_" + UUID.randomUUID().toString().replace("-", "");
    apiKeys.save(new ApiKey(t.getId(), "default", ApiKeyHasher.hash(raw), raw.substring(0, 12)));
    return new TenantAuth(t.getId(), raw);
  }

  private void appendCost(UUID tenantId, String actor, String model, String cost) {
    audit.append(
        new AuditEntry(
            tenantId,
            UUID.randomUUID(),
            actor,
            model,
            Verdict.ALLOWED,
            "prompt",
            null,
            Instant.now(),
            100,
            100,
            new BigDecimal(cost),
            Map.of()));
  }

  @Test
  void reportsSpendByModelByUserAndAProjection() throws Exception {
    TenantAuth a = newTenant();
    appendCost(a.tenantId(), "alice", "openai/gpt-4o", "0.50");
    appendCost(a.tenantId(), "alice", "openai/gpt-4o", "0.30");
    appendCost(a.tenantId(), "bob", "anthropic/claude-3", "0.20");

    String json =
        mvc.perform(get("/v1/usage/chargeback").header("Authorization", "Bearer " + a.key()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.costByUser.length()").value(2))
            .andExpect(jsonPath("$.costByModel.length()").value(2))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(((Number) JsonPath.read(json, "$.totalCostUsd")).doubleValue()).isEqualTo(1.00);
    assertThat(((Number) JsonPath.read(json, "$.costByModel['openai/gpt-4o']")).doubleValue())
        .isEqualTo(0.80);
    assertThat(((Number) JsonPath.read(json, "$.costByModel['anthropic/claude-3']")).doubleValue())
        .isEqualTo(0.20);
    // Three calls in the last 7 days project to a positive monthly run-rate.
    assertThat(((Number) JsonPath.read(json, "$.last30DaysCostUsd")).doubleValue()).isEqualTo(1.00);
    assertThat(((Number) JsonPath.read(json, "$.projectedMonthlyCostUsd")).doubleValue())
        .isGreaterThan(0.0);
  }
}

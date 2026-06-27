package com.auvex.gateway.demo;

import com.auvex.gateway.audit.AuditEntry;
import com.auvex.gateway.audit.AuditLogRepository;
import com.auvex.gateway.audit.AuditService;
import com.auvex.gateway.audit.Verdict;
import com.auvex.gateway.auth.ApiKeyHasher;
import com.auvex.gateway.auth.AuthenticatedTenant;
import com.auvex.gateway.config.DemoProperties;
import com.auvex.gateway.member.Member;
import com.auvex.gateway.member.MemberRepository;
import com.auvex.gateway.policy.Policy;
import com.auvex.gateway.policy.PolicyRepository;
import com.auvex.gateway.tenant.ApiKey;
import com.auvex.gateway.tenant.ApiKeyRepository;
import com.auvex.gateway.tenant.Tenant;
import com.auvex.gateway.tenant.TenantRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a sandbox demo tenant — key, sample policies and members, and a week of realistic governed
 * activity — on startup, so the console's "Try the live demo" button opens onto a populated org.
 * Only active when {@code auvex.demo.enabled} is true; idempotent (the org is created once, and the
 * sample audit trail only when the tenant has none).
 */
@Component
@ConditionalOnProperty(name = "auvex.demo.enabled", havingValue = "true")
public class DemoSeeder implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(DemoSeeder.class);

  private final TenantRepository tenants;
  private final ApiKeyRepository apiKeys;
  private final PolicyRepository policies;
  private final MemberRepository members;
  private final AuditService audit;
  private final AuditLogRepository auditEntries;
  private final DemoProperties properties;

  public DemoSeeder(
      TenantRepository tenants,
      ApiKeyRepository apiKeys,
      PolicyRepository policies,
      MemberRepository members,
      AuditService audit,
      AuditLogRepository auditEntries,
      DemoProperties properties) {
    this.tenants = tenants;
    this.apiKeys = apiKeys;
    this.policies = policies;
    this.members = members;
    this.audit = audit;
    this.auditEntries = auditEntries;
    this.properties = properties;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    UUID tenantId = ensureOrg();
    if (auditEntries.countByTenantId(tenantId) == 0) {
      seedActivity(tenantId);
      LOG.info("Seeded demo activity for tenant '{}'.", properties.tenantName());
    }
  }

  // Create the tenant, key, policies and members once; return the (existing or new) tenant id.
  private UUID ensureOrg() {
    String hash = ApiKeyHasher.hash(properties.key());
    Optional<ApiKey> existing = apiKeys.findByKeyHash(hash);
    if (existing.isPresent()) {
      return existing.get().getTenantId();
    }

    Tenant tenant = tenants.save(new Tenant(properties.tenantName(), "demo"));
    UUID id = tenant.getId();
    String prefix = properties.key().substring(0, Math.min(12, properties.key().length()));
    apiKeys.save(new ApiKey(id, "demo-explore", hash, prefix));

    policies.save(new Policy(id, "redact-pii-external", "redact", "data_type", "email", 100, true));
    policies.save(new Policy(id, "block-cards-hr", "deny", "data_type", "credit_card", 200, true));
    policies.save(new Policy(id, "allow-smart-model", "allow", "model", "openai/gpt-4o", 50, true));

    members.save(new Member(id, "maya@demo.auvex.io", "Maya Rodriguez", "owner", "active"));
    members.save(new Member(id, "james@demo.auvex.io", "James Cole", "security_admin", "active"));
    members.save(new Member(id, "aisha@demo.auvex.io", "Aisha Patel", "auditor", "active"));

    LOG.info("Seeded demo tenant '{}' with sample policies and members.", properties.tenantName());
    return id;
  }

  // Append a week of varied, hash-chained audit entries so every console view has real data.
  private void seedActivity(UUID tenantId) {
    String[] actors = {
      "maya@demo.auvex.io", "james@demo.auvex.io", "aisha@demo.auvex.io", "api-service"
    };
    String[] models = {
      "openai/gpt-4o",
      "openai/gpt-4o-mini",
      "anthropic/claude-3-5-sonnet-20241022",
      "google/gemini-1.5-pro"
    };
    String[] redactTypes = {"email", "credit_card", "us_ssn", "phone", "api_key"};

    // Attribute each seeded call to a real principal: the demo API key for service traffic, or the
    // acting member (looked up once and cached) for a human actor — so the console shows who acted.
    UUID demoKeyId =
        apiKeys.findByKeyHash(ApiKeyHasher.hash(properties.key())).map(ApiKey::getId).orElse(null);
    Map<String, UUID> memberCache = new HashMap<>();

    for (int i = 0; i < 24; i++) {
      String actor = actors[i % actors.length];
      String model = models[i % models.length];

      Verdict verdict;
      Map<String, Integer> redactions;
      String prompt;
      if (i % 7 == 3) {
        verdict = Verdict.BLOCKED;
        redactions = Map.of("credit_card", 1);
        prompt = "Charge card ‹CREDIT_CARD_REDACTED› for the overdue invoice";
      } else if (i % 2 == 0) {
        verdict = Verdict.REDACTED;
        String type = redactTypes[i % redactTypes.length];
        redactions = Map.of(type, 1 + (i % 3));
        prompt = "Send the summary to ‹" + type.toUpperCase(java.util.Locale.ROOT) + "_REDACTED›";
      } else {
        verdict = Verdict.ALLOWED;
        redactions = Map.of();
        prompt = "Summarize the quarterly earnings call transcript";
      }

      int promptTokens = 180 + (i * 37) % 900;
      int completionTokens = 90 + (i * 53) % 700;
      BigDecimal cost =
          BigDecimal.valueOf((promptTokens * 2.5 + completionTokens * 10.0) / 1_000_000.0)
              .setScale(6, RoundingMode.HALF_UP);
      Instant when = Instant.now().minus(i * 7L, ChronoUnit.HOURS);

      AuthenticatedTenant principal;
      if (actor.contains("@")) {
        UUID memberId =
            memberCache.computeIfAbsent(
                actor,
                email ->
                    members
                        .findByTenantIdAndEmail(tenantId, email)
                        .map(Member::getId)
                        .orElse(null));
        principal = new AuthenticatedTenant(tenantId, null, memberId, actor);
      } else {
        principal = new AuthenticatedTenant(tenantId, demoKeyId);
      }
      audit.append(
          new AuditEntry(
                  tenantId,
                  UUID.randomUUID(),
                  actor,
                  model,
                  verdict,
                  prompt,
                  null,
                  when,
                  promptTokens,
                  completionTokens,
                  cost,
                  redactions)
              .withPrincipal(principal));
    }
  }
}

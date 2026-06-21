package com.auvex.gateway.demo;

import com.auvex.gateway.auth.ApiKeyHasher;
import com.auvex.gateway.config.DemoProperties;
import com.auvex.gateway.member.Member;
import com.auvex.gateway.member.MemberRepository;
import com.auvex.gateway.policy.Policy;
import com.auvex.gateway.policy.PolicyRepository;
import com.auvex.gateway.tenant.ApiKey;
import com.auvex.gateway.tenant.ApiKeyRepository;
import com.auvex.gateway.tenant.Tenant;
import com.auvex.gateway.tenant.TenantRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a sandbox demo tenant (key + sample policies and members) on startup, so the console's "Try
 * the live demo" button works against a populated org. Only active when {@code auvex.demo.enabled}
 * is true; idempotent — it does nothing once the demo key already exists.
 */
@Component
@ConditionalOnProperty(name = "auvex.demo.enabled", havingValue = "true")
public class DemoSeeder implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(DemoSeeder.class);

  private final TenantRepository tenants;
  private final ApiKeyRepository apiKeys;
  private final PolicyRepository policies;
  private final MemberRepository members;
  private final DemoProperties properties;

  public DemoSeeder(
      TenantRepository tenants,
      ApiKeyRepository apiKeys,
      PolicyRepository policies,
      MemberRepository members,
      DemoProperties properties) {
    this.tenants = tenants;
    this.apiKeys = apiKeys;
    this.policies = policies;
    this.members = members;
    this.properties = properties;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    String hash = ApiKeyHasher.hash(properties.key());
    if (apiKeys.findByKeyHash(hash).isPresent()) {
      return; // already seeded
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
  }
}

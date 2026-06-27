package com.auvex.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.auvex.gateway.auth.ConsoleSession;
import com.auvex.gateway.auth.ConsoleSessionService;
import com.auvex.gateway.member.Member;
import com.auvex.gateway.member.MemberRepository;
import com.auvex.gateway.saml.SamlRelayStateService;
import com.auvex.gateway.saml.SamlTestCrypto;
import com.auvex.gateway.support.AbstractPostgresIntegrationTest;
import com.auvex.gateway.tenant.Tenant;
import com.auvex.gateway.tenant.TenantRepository;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end SAML: a genuinely signed assertion posted to the ACS provisions a member and mints a
 * console session, while a tampered one is refused — the proof that the whole flow works without a
 * live IdP (the IdP signing cert is supplied by the test).
 */
@AutoConfigureMockMvc
class SamlAcsIntegrationTest extends AbstractPostgresIntegrationTest {

  private static final String TENANT_SLUG = "saml-acme";

  @DynamicPropertySource
  static void samlProvider(DynamicPropertyRegistry registry) {
    registry.add("auvex.saml.providers.okta.entity-id", () -> SamlTestCrypto.ENTITY_ID);
    registry.add("auvex.saml.providers.okta.sso-url", () -> "https://idp.example/sso");
    registry.add("auvex.saml.providers.okta.signing-certificate", () -> SamlTestCrypto.CERT_PEM);
    registry.add("auvex.saml.providers.okta.sp-entity-id", () -> SamlTestCrypto.SP_ENTITY_ID);
    registry.add("auvex.saml.providers.okta.acs-url", () -> SamlTestCrypto.ACS_URL);
    registry.add("auvex.saml.providers.okta.tenant-slug", () -> TENANT_SLUG);
    registry.add("auvex.saml.providers.okta.auto-provision", () -> "true");
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantRepository tenants;
  @Autowired private MemberRepository members;
  @Autowired private SamlRelayStateService relayStates;
  @Autowired private ConsoleSessionService sessions;

  private UUID tenantId;

  @BeforeEach
  void ensureTenant() {
    tenantId =
        tenants
            .findBySlug(TENANT_SLUG)
            .orElseGet(() -> tenants.save(new Tenant("SAML Acme", TENANT_SLUG)))
            .getId();
  }

  @Test
  void acsAcceptsASignedAssertionProvisionsAMemberAndMintsASession() throws Exception {
    String requestId = "_req-" + UUID.randomUUID();
    String relayState = relayStates.mint("okta", requestId, 600);
    String email = "alice-" + UUID.randomUUID() + "@corp.example";
    String samlResponse =
        SamlTestCrypto.response().email(email).inResponseTo(requestId).buildBase64();

    MvcResult result =
        mvc.perform(
                post("/auth/saml/okta/acs")
                    .param("SAMLResponse", samlResponse)
                    .param("RelayState", relayState))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", containsString("#token=")))
            .andReturn();

    // The member was auto-provisioned for this tenant.
    Optional<Member> member = members.findByTenantIdAndEmail(tenantId, email);
    assertThat(member).isPresent();
    assertThat(member.get().getStatus()).isEqualTo("active");

    // The fragment carries a valid console session for that member.
    String location = result.getResponse().getHeader("Location");
    if (location == null) {
      throw new AssertionError("the ACS redirect must set a Location header");
    }
    String token = tokenFromFragment(location);
    Optional<ConsoleSession> session = sessions.verify(token);
    assertThat(session).isPresent();
    assertThat(session.get().email()).isEqualTo(email);
    assertThat(session.get().tenantId()).isEqualTo(tenantId);
  }

  @Test
  void acsRejectsATamperedAssertionWith401() throws Exception {
    String requestId = "_req-" + UUID.randomUUID();
    String relayState = relayStates.mint("okta", requestId, 600);
    String samlResponse =
        SamlTestCrypto.response().inResponseTo(requestId).tampered().buildBase64();

    mvc.perform(
            post("/auth/saml/okta/acs")
                .param("SAMLResponse", samlResponse)
                .param("RelayState", relayState))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.type").value("authentication_error"));
  }

  @Test
  void acsRejectsAForgedRelayStateWith401() throws Exception {
    String samlResponse = SamlTestCrypto.response().inResponseTo("_req-x").buildBase64();

    mvc.perform(
            post("/auth/saml/okta/acs")
                .param("SAMLResponse", samlResponse)
                .param("RelayState", "not.a.valid.relaystate"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void loginRedirectsToTheIdpWithASamlRequest() throws Exception {
    mvc.perform(get("/auth/saml/okta/login"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", containsString("https://idp.example/sso")))
        .andExpect(header().string("Location", containsString("SAMLRequest=")))
        .andExpect(header().string("Location", containsString("RelayState=")));
  }

  @Test
  void metadataDescribesOurServiceProvider() throws Exception {
    mvc.perform(get("/auth/saml/okta/metadata"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("EntityDescriptor")))
        .andExpect(content().string(containsString(SamlTestCrypto.SP_ENTITY_ID)))
        .andExpect(content().string(containsString(SamlTestCrypto.ACS_URL)));
  }

  @Test
  void unknownProviderIs404() throws Exception {
    mvc.perform(get("/auth/saml/nope/login")).andExpect(status().isNotFound());
  }

  private static String tokenFromFragment(String location) {
    int marker = location.indexOf("#token=");
    return URLDecoder.decode(
        location.substring(marker + "#token=".length()), StandardCharsets.UTF_8);
  }
}

package com.auvex.gateway;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.auvex.gateway.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Proves the OIDC login endpoint redirects to the provider with the right OAuth parameters. */
@AutoConfigureMockMvc
class OidcLoginIntegrationTest extends AbstractPostgresIntegrationTest {

  @DynamicPropertySource
  static void provider(DynamicPropertyRegistry registry) {
    registry.add("auvex.auth.sso.test.issuer", () -> "https://issuer.test");
    registry.add("auvex.auth.sso.test.client-id", () -> "client-abc");
    registry.add("auvex.auth.sso.test.client-secret", () -> "shh");
    registry.add("auvex.auth.sso.test.authorization-uri", () -> "https://issuer.test/authorize");
    registry.add("auvex.auth.sso.test.token-uri", () -> "https://issuer.test/token");
    registry.add("auvex.auth.sso.test.jwks-uri", () -> "https://issuer.test/jwks");
    registry.add("auvex.auth.sso.test.redirect-uri", () -> "https://gw/auth/oidc/test/callback");
    registry.add("auvex.auth.sso.test.tenant-slug", () -> "acme");
  }

  @Autowired private MockMvc mvc;

  @Test
  void redirectsToTheProviderWithOauthParams() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/auth/oidc/test/login"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", containsString("https://issuer.test/authorize")))
        .andExpect(header().string("Location", containsString("response_type=code")))
        .andExpect(header().string("Location", containsString("client_id=client-abc")))
        .andExpect(header().string("Location", containsString("state=")))
        .andExpect(header().string("Location", containsString("nonce=")));
  }

  @Test
  void unknownProviderIs404() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/auth/oidc/nope/login")).andExpect(status().isNotFound());
  }
}

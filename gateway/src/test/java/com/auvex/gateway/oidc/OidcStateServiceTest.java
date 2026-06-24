package com.auvex.gateway.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.auvex.gateway.config.AuthProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Proves the signed OAuth-state round-trips and that tampering or expiry is rejected. */
class OidcStateServiceTest {

  private final OidcStateService service =
      new OidcStateService(
          new AuthProperties("a-strong-test-secret-value-1234567890", null, false, Map.of()),
          new ObjectMapper());

  @Test
  void roundTripsProviderAndNonce() {
    String state = service.mint("google", "nonce-xyz", 600);
    OidcStateService.State parsed = service.verify(state);
    assertThat(parsed.provider()).isEqualTo("google");
    assertThat(parsed.nonce()).isEqualTo("nonce-xyz");
  }

  @Test
  void rejectsATamperedState() {
    String state = service.mint("google", "nonce-xyz", 600);
    // Flip the first payload character to a different valid base64url char, so the signed bytes
    // change and the HMAC can no longer match.
    char first = state.charAt(0);
    String tampered = (first == 'A' ? 'B' : 'A') + state.substring(1);
    assertThatThrownBy(() -> service.verify(tampered)).isInstanceOf(OidcException.class);
  }

  @Test
  void rejectsAnExpiredState() {
    String state = service.mint("google", "nonce-xyz", -1); // already expired
    assertThatThrownBy(() -> service.verify(state))
        .isInstanceOf(OidcException.class)
        .hasMessageContaining("expired");
  }

  @Test
  void rejectsMissingOrMalformedState() {
    assertThatThrownBy(() -> service.verify(null)).isInstanceOf(OidcException.class);
    assertThatThrownBy(() -> service.verify("not-a-token")).isInstanceOf(OidcException.class);
  }

  @Test
  void newNonceIsRandom() {
    assertThat(service.newNonce()).isNotEqualTo(service.newNonce());
  }
}

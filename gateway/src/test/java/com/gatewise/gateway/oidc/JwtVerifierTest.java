package com.gatewise.gateway.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatewise.gateway.config.AuthProperties.SsoProvider;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies the id_token verifier end-to-end with a real RSA keypair: we mint and sign a JWT the way
 * an OIDC provider would, hand the verifier the public key via a stub JWKS source, and assert it
 * accepts a good token and rejects every tampered or mismatched one.
 */
class JwtVerifierTest {

  private static final Base64.Encoder URL = Base64.getUrlEncoder().withoutPadding();
  private static final String KID = "test-key-1";
  private static final ObjectMapper JSON = new ObjectMapper();

  private static KeyPair keyPair;
  private static SsoProvider provider;
  private static JwtVerifier verifier;

  @BeforeAll
  static void setup() throws Exception {
    KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
    gen.initialize(2048);
    keyPair = gen.generateKeyPair();

    provider =
        new SsoProvider(
            "https://issuer.test",
            "client-123",
            "secret",
            "https://issuer.test/auth",
            "https://issuer.test/token",
            "https://issuer.test/jwks",
            "https://gw/callback",
            "/",
            null,
            "acme",
            true,
            "auditor");

    // Stub JWKS: always returns our generated public key, ignoring the uri/kid lookup.
    JwkSource stub =
        new JwkSource() {
          @Override
          public PublicKey publicKey(String jwksUri, String kid) {
            return keyPair.getPublic();
          }
        };
    verifier = new JwtVerifier(stub, JSON);
  }

  @Test
  void acceptsAValidToken() {
    String token = signedToken(claims(Instant.now().plusSeconds(3600), "client-123", "nonce-1"));
    OidcUser user = verifier.verify(token, provider, "nonce-1");
    assertThat(user.email()).isEqualTo("user@example.com");
    assertThat(user.emailVerified()).isTrue();
    assertThat(user.subject()).isEqualTo("sub-1");
  }

  @Test
  void rejectsAnExpiredToken() {
    String token = signedToken(claims(Instant.now().minusSeconds(60), "client-123", "nonce-1"));
    assertThatThrownBy(() -> verifier.verify(token, provider, "nonce-1"))
        .isInstanceOf(OidcException.class)
        .hasMessageContaining("expired");
  }

  @Test
  void rejectsAMismatchedNonce() {
    String token = signedToken(claims(Instant.now().plusSeconds(3600), "client-123", "nonce-1"));
    assertThatThrownBy(() -> verifier.verify(token, provider, "different-nonce"))
        .isInstanceOf(OidcException.class)
        .hasMessageContaining("nonce");
  }

  @Test
  void rejectsAWrongAudience() {
    String token = signedToken(claims(Instant.now().plusSeconds(3600), "someone-else", "nonce-1"));
    assertThatThrownBy(() -> verifier.verify(token, provider, "nonce-1"))
        .isInstanceOf(OidcException.class)
        .hasMessageContaining("audience");
  }

  @Test
  void rejectsATamperedSignature() {
    String token = signedToken(claims(Instant.now().plusSeconds(3600), "client-123", "nonce-1"));
    String tampered = token.substring(0, token.length() - 4) + "AAAA";
    assertThatThrownBy(() -> verifier.verify(tampered, provider, "nonce-1"))
        .isInstanceOf(OidcException.class);
  }

  private static String claims(Instant exp, String aud, String nonce) {
    return "{\"iss\":\"https://issuer.test\",\"aud\":\""
        + aud
        + "\",\"exp\":"
        + exp.getEpochSecond()
        + ",\"nonce\":\""
        + nonce
        + "\",\"email\":\"user@example.com\",\"email_verified\":true,"
        + "\"sub\":\"sub-1\",\"name\":\"User Example\"}";
  }

  private static String signedToken(String claimsJson) {
    String header = "{\"alg\":\"RS256\",\"kid\":\"" + KID + "\",\"typ\":\"JWT\"}";
    String unsigned =
        URL.encodeToString(header.getBytes(StandardCharsets.UTF_8))
            + "."
            + URL.encodeToString(claimsJson.getBytes(StandardCharsets.UTF_8));
    try {
      Signature rsa = Signature.getInstance("SHA256withRSA");
      rsa.initSign(keyPair.getPrivate());
      rsa.update(unsigned.getBytes(StandardCharsets.UTF_8));
      return unsigned + "." + URL.encodeToString(rsa.sign());
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }
}

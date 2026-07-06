package com.gatewise.gateway.oidc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatewise.gateway.config.AuthProperties.SsoProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * Verifies an OIDC {@code id_token} (a signed JWT) and extracts the user.
 *
 * <p>Checks performed: RS256 signature against the provider's JWKS key (by {@code kid}), issuer
 * matches the configured issuer, audience contains our client id, the token hasn't expired, and the
 * {@code nonce} matches the one minted at login (binding the token to this exact sign-in, which
 * defeats replay). The crypto is the standard library — no external JWT dependency.
 */
@Component
public class JwtVerifier {

  private static final Base64.Decoder URL = Base64.getUrlDecoder();

  private final JwkSource jwks;
  private final ObjectMapper json;

  public JwtVerifier(JwkSource jwks, ObjectMapper json) {
    this.jwks = jwks;
    this.json = json;
  }

  /** Verifies the token for {@code provider} and the expected {@code nonce}; returns the user. */
  public OidcUser verify(String idToken, SsoProvider provider, String expectedNonce) {
    String[] parts = idToken == null ? new String[0] : idToken.split("\\.");
    if (parts.length != 3) {
      throw new OidcException("Malformed id_token.");
    }
    JsonNode header = parse(parts[0], "header");
    JsonNode claims = parse(parts[1], "claims");

    if (!"RS256".equals(header.path("alg").asText())) {
      throw new OidcException("Unsupported id_token signing algorithm; RS256 required.");
    }
    verifySignature(parts, provider, header.path("kid").asText());

    requireIssuer(claims, provider);
    requireAudience(claims, provider.clientId());
    requireNotExpired(claims);
    requireNonce(claims, expectedNonce);

    String email = claims.path("email").asText("");
    if (email.isBlank()) {
      throw new OidcException("id_token has no email claim; cannot identify the user.");
    }
    return new OidcUser(
        claims.path("sub").asText(""),
        email,
        claims.path("email_verified").asBoolean(false),
        claims.path("name").asText(""));
  }

  private void verifySignature(String[] parts, SsoProvider provider, String kid) {
    PublicKey key = jwks.publicKey(provider.jwksUri(), kid);
    try {
      Signature rsa = Signature.getInstance("SHA256withRSA");
      rsa.initVerify(key);
      rsa.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8));
      if (!rsa.verify(URL.decode(parts[2]))) {
        throw new OidcException("id_token signature is invalid.");
      }
    } catch (java.security.GeneralSecurityException e) {
      throw new OidcException("Failed to verify the id_token signature.", e);
    }
  }

  private void requireIssuer(JsonNode claims, SsoProvider provider) {
    String iss = strip(claims.path("iss").asText(""));
    if (!iss.equals(strip(provider.issuer()))) {
      throw new OidcException("id_token issuer does not match the configured provider.");
    }
  }

  // aud may be a single string or an array; our client id must be present.
  private void requireAudience(JsonNode claims, String clientId) {
    JsonNode aud = claims.path("aud");
    boolean ok =
        aud.isTextual()
            ? aud.asText().equals(clientId)
            : aud.isArray() && audArrayContains(aud, clientId);
    if (!ok) {
      throw new OidcException("id_token audience does not include this client.");
    }
  }

  private boolean audArrayContains(JsonNode aud, String clientId) {
    for (JsonNode a : aud) {
      if (a.asText().equals(clientId)) {
        return true;
      }
    }
    return false;
  }

  private void requireNotExpired(JsonNode claims) {
    long exp = claims.path("exp").asLong(0);
    if (exp <= 0 || Instant.ofEpochSecond(exp).isBefore(Instant.now())) {
      throw new OidcException("id_token has expired.");
    }
  }

  private void requireNonce(JsonNode claims, String expectedNonce) {
    String nonce = claims.path("nonce").asText("");
    if (expectedNonce == null || expectedNonce.isBlank() || !expectedNonce.equals(nonce)) {
      throw new OidcException("id_token nonce does not match the login request.");
    }
  }

  private JsonNode parse(String part, String which) {
    try {
      return json.readTree(URL.decode(part));
    } catch (IOException | IllegalArgumentException e) {
      throw new OidcException("id_token " + which + " is not valid base64url JSON.", e);
    }
  }

  private static String strip(String issuer) {
    return issuer == null ? "" : issuer.replaceAll("/+$", "");
  }
}

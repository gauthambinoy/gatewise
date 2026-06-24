package com.auvex.gateway.oidc;

import com.auvex.gateway.config.AuthProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Mints and verifies the OAuth {@code state} parameter — an HMAC-signed, short-lived token that
 * carries the provider name and the login {@code nonce}.
 *
 * <p>Because the provider echoes {@code state} back unchanged on the callback, signing it makes the
 * whole flow stateless and tamper-proof (CSRF protection) without any server-side storage: an
 * attacker can't forge a state, and the embedded nonce binds the returned id_token to this sign-in.
 */
@Component
public class OidcStateService {

  private static final String HMAC = "HmacSHA256";
  private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DEC = Base64.getUrlDecoder();
  private static final SecureRandom RANDOM = new SecureRandom();

  private final ObjectMapper json;
  private final byte[] key;

  public OidcStateService(AuthProperties properties, ObjectMapper json) {
    this.json = json;
    this.key = properties.sessionSecret().getBytes(StandardCharsets.UTF_8);
  }

  /** A fresh random nonce for the login request. */
  public String newNonce() {
    byte[] bytes = new byte[24];
    RANDOM.nextBytes(bytes);
    return ENC.encodeToString(bytes);
  }

  /** Signs a {@code state} token valid for {@code ttlSeconds}, binding the provider and nonce. */
  public String mint(String provider, String nonce, long ttlSeconds) {
    try {
      State state = new State(provider, nonce, Instant.now().getEpochSecond() + ttlSeconds);
      byte[] payload = json.writeValueAsBytes(state);
      return ENC.encodeToString(payload) + "." + ENC.encodeToString(hmac(payload));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize OAuth state", e);
    }
  }

  /** Verifies a {@code state} token's signature and expiry; throws {@link OidcException} if bad. */
  public State verify(String state) {
    if (state == null) {
      throw new OidcException("Missing OAuth state.");
    }
    int dot = state.indexOf('.');
    if (dot <= 0 || dot == state.length() - 1) {
      throw new OidcException("Malformed OAuth state.");
    }
    try {
      byte[] payload = DEC.decode(state.substring(0, dot));
      byte[] signature = DEC.decode(state.substring(dot + 1));
      if (!MessageDigest.isEqual(signature, hmac(payload))) {
        throw new OidcException("OAuth state signature is invalid (possible CSRF).");
      }
      State parsed = json.readValue(payload, State.class);
      if (parsed.exp() < Instant.now().getEpochSecond()) {
        throw new OidcException("OAuth state has expired; restart the login.");
      }
      return parsed;
    } catch (IOException | IllegalArgumentException e) {
      throw new OidcException("Malformed OAuth state.", e);
    }
  }

  private byte[] hmac(byte[] data) {
    try {
      Mac mac = Mac.getInstance(HMAC);
      mac.init(new SecretKeySpec(key, HMAC));
      return mac.doFinal(data);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HMAC failure", e);
    }
  }

  /** The signed state payload: which provider, the login nonce, and an absolute expiry (epoch s). */
  public record State(String provider, String nonce, long exp) {}
}

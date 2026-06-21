package com.auvex.gateway.auth;

import com.auvex.gateway.config.AuthProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Mints and verifies console session tokens.
 *
 * <p>A token is a compact {@code payload.signature} pair: the JSON session, and an HMAC-SHA256 over
 * it keyed by the configured secret — a minimal JWS, no external library. Verification recomputes
 * the HMAC (constant-time compare) and rejects anything tampered or expired. This is the session a
 * console login produces; a real OIDC (Google/Okta) sign-in would mint exactly the same token.
 */
@Component
public class ConsoleSessionService {

  private static final String HMAC = "HmacSHA256";
  private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DEC = Base64.getUrlDecoder();

  private final ObjectMapper json;
  private final byte[] key;

  public ConsoleSessionService(AuthProperties properties, ObjectMapper json) {
    this.json = json;
    this.key = properties.sessionSecret().getBytes(StandardCharsets.UTF_8);
  }

  /** Signs a session into a token string. */
  public String mint(ConsoleSession session) {
    try {
      byte[] payload = json.writeValueAsBytes(session);
      return ENC.encodeToString(payload) + "." + ENC.encodeToString(hmac(payload));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize session", e);
    }
  }

  /** Returns the session iff the token's signature is valid and it hasn't expired. */
  public Optional<ConsoleSession> verify(String token) {
    if (token == null) {
      return Optional.empty();
    }
    int dot = token.indexOf('.');
    if (dot <= 0 || dot == token.length() - 1) {
      return Optional.empty();
    }
    try {
      byte[] payload = DEC.decode(token.substring(0, dot));
      byte[] signature = DEC.decode(token.substring(dot + 1));
      if (!MessageDigest.isEqual(signature, hmac(payload))) {
        return Optional.empty();
      }
      ConsoleSession session = json.readValue(payload, ConsoleSession.class);
      if (session.expiresAt() == null || session.expiresAt().isBefore(Instant.now())) {
        return Optional.empty();
      }
      return Optional.of(session);
    } catch (IOException | IllegalArgumentException e) {
      return Optional.empty(); // malformed token
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
}

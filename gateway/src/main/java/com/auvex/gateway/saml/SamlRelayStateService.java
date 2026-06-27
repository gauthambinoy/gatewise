package com.auvex.gateway.saml;

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
 * Mints and verifies the SAML {@code RelayState} — an HMAC-signed, short-lived token carrying the
 * provider name and the {@code AuthnRequest} id we generated at login.
 *
 * <p>The IdP echoes RelayState back unchanged on the ACS POST, so signing it keeps the flow
 * stateless yet tamper-proof: the embedded request id lets the ACS insist the assertion's {@code
 * InResponseTo} matches the request we actually started, which defeats replay and cross-login
 * injection. It reuses the same session secret as the rest of the console.
 */
@Component
public class SamlRelayStateService {

  private static final String HMAC = "HmacSHA256";
  private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DEC = Base64.getUrlDecoder();
  private static final SecureRandom RANDOM = new SecureRandom();

  private final ObjectMapper json;
  private final byte[] key;

  public SamlRelayStateService(AuthProperties properties, ObjectMapper json) {
    this.json = json;
    this.key = properties.sessionSecret().getBytes(StandardCharsets.UTF_8);
  }

  /** A fresh {@code AuthnRequest} id; the leading letter keeps it a valid XML NCName. */
  public String newRequestId() {
    byte[] bytes = new byte[20];
    RANDOM.nextBytes(bytes);
    return "_a" + ENC.encodeToString(bytes).replace("-", "").replace("_", "");
  }

  /** Signs a RelayState valid for {@code ttlSeconds}, binding the provider and request id. */
  public String mint(String provider, String requestId, long ttlSeconds) {
    try {
      RelayState state =
          new RelayState(provider, requestId, Instant.now().getEpochSecond() + ttlSeconds);
      byte[] payload = json.writeValueAsBytes(state);
      return ENC.encodeToString(payload) + "." + ENC.encodeToString(hmac(payload));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize SAML RelayState", e);
    }
  }

  /**
   * Verifies a RelayState's signature and expiry; throws {@link SamlException} if either is bad.
   */
  public RelayState verify(String relayState) {
    if (relayState == null) {
      throw new SamlException("Missing SAML RelayState.");
    }
    int dot = relayState.indexOf('.');
    if (dot <= 0 || dot == relayState.length() - 1) {
      throw new SamlException("Malformed SAML RelayState.");
    }
    try {
      byte[] payload = DEC.decode(relayState.substring(0, dot));
      byte[] signature = DEC.decode(relayState.substring(dot + 1));
      if (!MessageDigest.isEqual(signature, hmac(payload))) {
        throw new SamlException("SAML RelayState signature is invalid (possible CSRF).");
      }
      RelayState parsed = json.readValue(payload, RelayState.class);
      if (parsed.exp() < Instant.now().getEpochSecond()) {
        throw new SamlException("SAML RelayState has expired; restart the login.");
      }
      return parsed;
    } catch (IOException | IllegalArgumentException e) {
      throw new SamlException("Malformed SAML RelayState.", e);
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

  /** The signed RelayState payload: provider, the AuthnRequest id, and an absolute expiry. */
  public record RelayState(String provider, String requestId, long exp) {}
}

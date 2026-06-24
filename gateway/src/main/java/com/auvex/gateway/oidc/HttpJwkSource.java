package com.auvex.gateway.oidc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Fetches and caches a provider's JWKS, exposing the RSA public key for a given {@code kid}.
 *
 * <p>Keys are cached per JWKS URI for a few minutes (they rotate rarely); a cache miss on the kid
 * triggers a refresh, so a freshly-rotated key is picked up without waiting for the TTL.
 */
@Component
public class HttpJwkSource implements JwkSource {

  private static final Base64.Decoder URL = Base64.getUrlDecoder();
  private static final Duration TTL = Duration.ofMinutes(10);

  private final HttpClient http;
  private final ObjectMapper json;
  private final Map<String, Cached> cache = new ConcurrentHashMap<>();

  public HttpJwkSource(ObjectMapper json) {
    this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    this.json = json;
  }

  @Override
  public PublicKey publicKey(String jwksUri, String kid) {
    Map<String, PublicKey> keys = keysFor(jwksUri, false);
    PublicKey key = keys.get(kid);
    if (key == null) {
      key = keysFor(jwksUri, true).get(kid); // force a refresh in case the key just rotated
    }
    if (key == null) {
      throw new OidcException("No JWKS key matches the id_token kid '" + kid + "'.");
    }
    return key;
  }

  private Map<String, PublicKey> keysFor(String jwksUri, boolean forceRefresh) {
    Cached cached = cache.get(jwksUri);
    if (!forceRefresh && cached != null && cached.expiresAt.isAfter(java.time.Instant.now())) {
      return cached.keys;
    }
    Map<String, PublicKey> keys = fetch(jwksUri);
    cache.put(jwksUri, new Cached(keys, java.time.Instant.now().plus(TTL)));
    return keys;
  }

  private Map<String, PublicKey> fetch(String jwksUri) {
    try {
      HttpResponse<String> response =
          http.send(
              HttpRequest.newBuilder(URI.create(jwksUri)).timeout(Duration.ofSeconds(10)).GET().build(),
              HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new OidcException("JWKS endpoint returned HTTP " + response.statusCode() + ".");
      }
      Map<String, PublicKey> keys = new ConcurrentHashMap<>();
      for (JsonNode jwk : json.readTree(response.body()).path("keys")) {
        if ("RSA".equals(jwk.path("kty").asText())) {
          keys.put(jwk.path("kid").asText(), rsaKey(jwk));
        }
      }
      return keys;
    } catch (IOException e) {
      throw new OidcException("Failed to fetch JWKS from " + jwksUri + ".", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new OidcException("Interrupted while fetching JWKS.", e);
    }
  }

  private PublicKey rsaKey(JsonNode jwk) {
    try {
      BigInteger modulus = new BigInteger(1, URL.decode(jwk.path("n").asText()));
      BigInteger exponent = new BigInteger(1, URL.decode(jwk.path("e").asText()));
      return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));
    } catch (java.security.GeneralSecurityException | IllegalArgumentException e) {
      throw new OidcException("Malformed RSA JWK in the provider JWKS.", e);
    }
  }

  private record Cached(Map<String, PublicKey> keys, java.time.Instant expiresAt) {}
}

package com.auvex.gateway.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Hashes raw API keys so only the digest is ever stored or compared.
 *
 * <p>Authentication hashes the presented key and looks it up by hash, so the raw secret never
 * touches the database. SHA-256 is a deterministic one-way function — the same key always yields
 * the same hash, which is exactly what a lookup needs.
 */
public final class ApiKeyHasher {

  private ApiKeyHasher() {}

  /** Returns the lowercase hex SHA-256 of the given raw key. */
  public static String hash(String rawKey) {
    try {
      MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
      byte[] digest = sha256.digest(rawKey.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      // Every conformant JVM ships SHA-256, so this branch is effectively unreachable.
      throw new IllegalStateException("SHA-256 algorithm unavailable", e);
    }
  }
}

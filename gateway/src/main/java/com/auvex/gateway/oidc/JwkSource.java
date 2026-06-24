package com.auvex.gateway.oidc;

import java.security.PublicKey;

/**
 * Resolves an id_token signing key by {@code kid} from a provider's JWKS endpoint. Abstracted so
 * the verifier can be unit-tested against an in-memory key without any network.
 */
public interface JwkSource {

  /**
   * The public key for {@code kid} published at {@code jwksUri}, or throws if it can't be found.
   */
  PublicKey publicKey(String jwksUri, String kid);
}

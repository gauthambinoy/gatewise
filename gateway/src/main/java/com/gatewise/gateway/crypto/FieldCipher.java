package com.gatewise.gateway.crypto;

/**
 * Encrypts and decrypts a single string field at rest.
 *
 * <p>This is the seam for "bring your own KMS". The built-in implementation ({@link
 * AesGcmFieldCipher}) holds an AES-256 key supplied through configuration, which is the right
 * default for self-hosting. An operator who keeps their keys in a managed KMS can supply a
 * different implementation — one that calls AWS KMS, GCP KMS, Vault, etc. to wrap/unwrap a data key
 * — without touching anything that stores audit data, because every call site depends on this
 * interface rather than a concrete cipher. The contract both implementations must keep is in {@link
 * #decrypt}: a value that doesn't carry the cipher's version marker is already plaintext and must
 * be returned untouched.
 */
public interface FieldCipher {

  /** Returns the value to store. May be the input unchanged (e.g. when encryption is off). */
  String encrypt(String plaintext);

  /**
   * Returns the original plaintext for a stored value. Any value that doesn't carry the cipher's
   * version marker is treated as already-plaintext and returned unchanged, so plaintext rows
   * written before encryption was switched on keep reading correctly.
   */
  String decrypt(String stored);
}

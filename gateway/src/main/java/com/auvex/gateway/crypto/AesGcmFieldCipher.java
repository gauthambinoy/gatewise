package com.auvex.gateway.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * The built-in {@link FieldCipher}: AES-256-GCM with a symmetric key from configuration.
 *
 * <p>Each value gets a fresh random 12-byte IV, so encrypting the same plaintext twice yields
 * different ciphertext. The IV is prepended to the GCM output (ciphertext + auth tag), the whole
 * lot is base64-encoded, and a short version marker is added so we can tell encrypted values apart
 * from plaintext and could evolve the format later without a migration.
 *
 * <p>Stored form: {@code "encv1:" + base64( iv(12) || ciphertext || gcmTag(16) )}.
 *
 * <p>{@code enabled} gates only writes: when off, {@link #encrypt} stores plaintext. Decryption is
 * always marker-driven — an unmarked value is passed through, a marked one is decrypted — so a key
 * left configured after the flag is turned off still reads previously-encrypted rows. GCM
 * authenticates the data, so any tampering with a stored value is caught and fails closed.
 */
public final class AesGcmFieldCipher implements FieldCipher {

  /** Prefix on every encrypted value; its absence means "already plaintext". */
  static final String MARKER = "encv1:";

  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int IV_BYTES = 12; // 96-bit nonce — the size GCM is defined for
  private static final int TAG_BITS = 128; // full-strength authentication tag
  private static final int KEY_BYTES = 32; // AES-256

  private final boolean enabled;
  private final SecretKeySpec key; // null only when no key is configured
  private final SecureRandom random = new SecureRandom();

  /**
   * @param enabled whether new values are encrypted on write
   * @param base64Key base64 of a 32-byte key; required when {@code enabled}, otherwise optional
   */
  public AesGcmFieldCipher(boolean enabled, String base64Key) {
    this.enabled = enabled;
    boolean hasKey = base64Key != null && !base64Key.isBlank();
    if (enabled && !hasKey) {
      throw new IllegalStateException(
          "auvex.encryption.enabled=true requires auvex.encryption.key (base64 of 32 bytes)");
    }
    this.key = hasKey ? parseKey(base64Key) : null;
  }

  private static SecretKeySpec parseKey(String base64Key) {
    byte[] raw;
    try {
      raw = Base64.getDecoder().decode(base64Key.trim());
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("auvex.encryption.key must be valid base64", e);
    }
    if (raw.length != KEY_BYTES) {
      throw new IllegalStateException(
          "auvex.encryption.key must decode to 32 bytes for AES-256; got " + raw.length);
    }
    return new SecretKeySpec(raw, "AES");
  }

  @Override
  public String encrypt(String plaintext) {
    // Nulls stay null; when encryption is off we store the value as-is.
    if (plaintext == null || !enabled) {
      return plaintext;
    }
    try {
      byte[] iv = new byte[IV_BYTES];
      random.nextBytes(iv);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      byte[] combined = new byte[iv.length + ciphertext.length];
      System.arraycopy(iv, 0, combined, 0, iv.length);
      System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
      return MARKER + Base64.getEncoder().encodeToString(combined);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to encrypt field", e);
    }
  }

  @Override
  public String decrypt(String stored) {
    // Toggle-safety / pass-through: a value without the marker is already plaintext (written before
    // encryption was switched on, or while it was off), and with no key configured we can't decrypt
    // anything anyway. In both cases hand the value back untouched, so flipping the flag never
    // corrupts existing data and a stray encrypted value never breaks an unrelated read.
    if (stored == null || key == null || !stored.startsWith(MARKER)) {
      return stored;
    }
    try {
      byte[] combined = Base64.getDecoder().decode(stored.substring(MARKER.length()));
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, combined, 0, IV_BYTES));
      byte[] plaintext = cipher.doFinal(combined, IV_BYTES, combined.length - IV_BYTES);
      return new String(plaintext, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) {
      // A failed GCM tag check means the stored bytes were altered (or the wrong key was used).
      // Surface it instead of silently returning garbage.
      throw new IllegalStateException("Failed to decrypt field (tampered or wrong key)", e);
    }
  }
}

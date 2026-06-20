package com.auvex.gateway.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

/**
 * Builds the tamper-evident hash chain over audit entries.
 *
 * <p>Each entry's hash folds in the previous entry's hash, so altering any past field breaks every
 * link after it. Chains are per tenant; the first entry links to {@link #GENESIS}.
 */
public final class AuditChain {

  /** The {@code prev_hash} of the first entry in any tenant's chain (64 zeros). */
  public static final String GENESIS = "0".repeat(64);

  private AuditChain() {}

  /** {@code entry_hash = SHA-256( ascii(prevHash) || utf8(canonical(entry)) )}, lowercase hex. */
  public static String entryHash(String prevHash, AuditEntry entry) {
    try {
      MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
      sha256.update(prevHash.getBytes(StandardCharsets.US_ASCII));
      sha256.update(canonical(entry).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(sha256.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm unavailable", e);
    }
  }

  /** The unambiguous, length-prefixed serialization that the hash is taken over. */
  public static String canonical(AuditEntry entry) {
    StringBuilder sb = new StringBuilder();
    frame(sb, entry.tenantId().toString());
    frame(sb, entry.requestId().toString());
    frame(sb, entry.actor());
    frame(sb, entry.model());
    frame(sb, entry.verdict().value());
    frame(sb, entry.promptRedacted());
    frame(sb, entry.responseRedacted());
    frame(
        sb, DateTimeFormatter.ISO_INSTANT.format(entry.createdAt().truncatedTo(ChronoUnit.MICROS)));
    return sb.toString();
  }

  // Length-prefixed framing so attacker-influenced text can't shift field boundaries; a null is
  // encoded ("-1:;") distinctly from an empty string ("0:;").
  private static void frame(StringBuilder sb, String value) {
    if (value == null) {
      sb.append("-1:;");
      return;
    }
    int byteLength = value.getBytes(StandardCharsets.UTF_8).length;
    sb.append(byteLength).append(':').append(value).append(';');
  }
}

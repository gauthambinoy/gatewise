package com.gatewise.gateway.approval;

import com.gatewise.gateway.config.ApprovalProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The human-in-the-loop review gate. Decides whether a call must be held for approval and, if so,
 * records (or reuses) a pending {@link HeldRequest}; an already-approved prompt is let straight
 * through. The approval ties to a hash of the tenant + redacted prompt, so approving a prompt lets
 * an identical retry proceed while a different prompt is still reviewed.
 */
@Service
public class ApprovalService {

  private final HeldRequestRepository held;
  private final ApprovalProperties properties;

  public ApprovalService(HeldRequestRepository held, ApprovalProperties properties) {
    this.held = held;
    this.properties = properties;
  }

  public boolean enabled() {
    return properties.enabled();
  }

  /**
   * If the call must be reviewed and isn't already approved, returns the pending hold's id (the
   * caller should respond 202). Returns empty when no review is needed or the prompt is approved.
   */
  public Optional<UUID> reviewIfNeeded(
      UUID tenantId,
      String actor,
      String model,
      String promptRedacted,
      boolean injectionDetected,
      Set<String> dataTypes) {

    if (!properties.enabled()) {
      return Optional.empty();
    }
    String reason = reasonFor(injectionDetected, dataTypes);
    if (reason == null) {
      return Optional.empty();
    }

    String hash = hash(tenantId, promptRedacted);
    // Already approved → let the (identical) prompt through.
    if (held.findFirstByTenantIdAndPromptHashAndStatusOrderByCreatedAtDesc(
            tenantId, hash, "approved")
        .isPresent()) {
      return Optional.empty();
    }
    // Reuse an existing pending hold for the same prompt instead of piling up duplicates.
    HeldRequest pending =
        held.findFirstByTenantIdAndPromptHashAndStatusOrderByCreatedAtDesc(
                tenantId, hash, "pending")
            .orElseGet(
                () ->
                    held.save(
                        new HeldRequest(tenantId, actor, model, hash, promptRedacted, reason)));
    return Optional.of(pending.getId());
  }

  // Why a call is held, or null when nothing triggers a review.
  private String reasonFor(boolean injectionDetected, Set<String> dataTypes) {
    if (properties.reviewInjection() && injectionDetected) {
      return "prompt-injection detected";
    }
    Set<String> triggers = new TreeSet<>(properties.reviewDataTypeSet());
    triggers.retainAll(dataTypes);
    if (!triggers.isEmpty()) {
      return "sensitive data: " + String.join(", ", triggers);
    }
    return null;
  }

  private static String hash(UUID tenantId, String promptRedacted) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest((tenantId + "|" + promptRedacted).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required", e);
    }
  }
}

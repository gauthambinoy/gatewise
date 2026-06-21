package com.auvex.gateway.web;

import com.auvex.gateway.audit.AuditLog;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** The public representation of one audit entry, including its chain hashes, tokens and cost. */
public record AuditView(
    Long id,
    UUID requestId,
    String actor,
    String model,
    String verdict,
    String promptRedacted,
    String prevHash,
    String entryHash,
    OffsetDateTime createdAt,
    Integer promptTokens,
    Integer completionTokens,
    BigDecimal costUsd) {

  /** Projects a stored audit row into its API view. */
  public static AuditView of(AuditLog row) {
    return new AuditView(
        row.getId(),
        row.getRequestId(),
        row.getActor(),
        row.getModel(),
        row.getVerdict(),
        row.getPromptRedacted(),
        row.getPrevHash(),
        row.getEntryHash(),
        row.getCreatedAt(),
        row.getPromptTokens(),
        row.getCompletionTokens(),
        row.getCostUsd());
  }
}

package com.auvex.gateway.config;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Human-in-the-loop approval settings: which calls must be held for a reviewer before they're sent.
 *
 * @param enabled turn the approval workflow on (off by default)
 * @param reviewInjection hold a call when prompt-injection is detected
 * @param reviewDataTypes hold a call when it contains any of these redacted data types (e.g. {@code
 *     credit_card})
 */
@ConfigurationProperties(prefix = "auvex.approval")
public record ApprovalProperties(
    boolean enabled, boolean reviewInjection, List<String> reviewDataTypes) {

  public ApprovalProperties {
    reviewDataTypes =
        reviewDataTypes == null
            ? List.of()
            : reviewDataTypes.stream().map(t -> t.toLowerCase(Locale.ROOT)).toList();
  }

  /** The review-trigger data types as a lowercase set. */
  public Set<String> reviewDataTypeSet() {
    return reviewDataTypes.stream().collect(Collectors.toUnmodifiableSet());
  }
}

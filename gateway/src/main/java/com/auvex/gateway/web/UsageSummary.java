package com.auvex.gateway.web;

import java.util.Map;

/**
 * A per-tenant usage summary derived from the audit log.
 *
 * <p>Per-token cost is intentionally absent: it needs the provider's token counts from the
 * response, which the streaming proxy doesn't yet capture (tracked in Known issues).
 */
public record UsageSummary(
    long totalCalls,
    long allowed,
    long blocked,
    long redacted,
    long errored,
    Map<String, Long> byModel) {}

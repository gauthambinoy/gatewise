package com.auvex.gateway.web;

import java.math.BigDecimal;
import java.util.Map;

/** A per-tenant usage summary derived from the audit log, including tokens and cost. */
public record UsageSummary(
    long totalCalls,
    long allowed,
    long blocked,
    long redacted,
    long errored,
    Map<String, Long> byModel,
    BigDecimal totalCostUsd,
    long totalTokens) {}

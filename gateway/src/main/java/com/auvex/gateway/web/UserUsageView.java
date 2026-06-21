package com.auvex.gateway.web;

import java.math.BigDecimal;

/** Per-user (per-actor) usage, for the Users page. */
public record UserUsageView(
    String actor, long requests, long redacted, long blocked, BigDecimal costUsd) {}

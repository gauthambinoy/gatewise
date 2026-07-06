package com.gatewise.gateway.auth;

import java.time.Instant;
import java.util.UUID;

/** A signed-in console member's session — the principal behind a console request. */
public record ConsoleSession(
    UUID tenantId, UUID memberId, String email, String role, Instant expiresAt) {}

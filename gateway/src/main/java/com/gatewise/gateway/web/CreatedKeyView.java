package com.gatewise.gateway.web;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The response when a key is created — the only time the raw {@code secret} is returned. Store it
 * now; the server keeps only a hash and can never show it again.
 */
public record CreatedKeyView(
    UUID id, String name, String prefix, String status, OffsetDateTime createdAt, String secret) {}

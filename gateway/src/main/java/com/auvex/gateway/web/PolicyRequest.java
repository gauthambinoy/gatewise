package com.auvex.gateway.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * The body for creating or updating a policy rule.
 *
 * <p>{@code priority} and {@code enabled} are optional — they default to 100 and true. The effect
 * and resource type are constrained to the values the engine understands.
 */
public record PolicyRequest(
    @NotBlank String name,
    @NotBlank
        @Pattern(
            regexp = "allow|deny|redact",
            message = "effect must be 'allow', 'deny' or 'redact'")
        String effect,
    @NotBlank
        @Pattern(
            regexp = "model|data_type|user",
            message = "resourceType must be 'model', 'data_type' or 'user'")
        String resourceType,
    @NotBlank String resourceValue,
    Integer priority,
    Boolean enabled) {}

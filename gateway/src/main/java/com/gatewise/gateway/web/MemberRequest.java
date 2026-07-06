package com.gatewise.gateway.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * The body for inviting or updating a console member.
 *
 * <p>{@code status} is optional (defaults to {@code invited}); the role is constrained to the
 * values the console understands.
 */
public record MemberRequest(
    @NotBlank @Email String email,
    String name,
    @NotBlank
        @Pattern(
            regexp = "owner|security_admin|auditor",
            message = "role must be 'owner', 'security_admin' or 'auditor'")
        String role,
    String status) {}

package com.auvex.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Role-based access control for the console management API.
 *
 * @param enabled when on, {@code /v1} management endpoints (policies, members, keys, approvals,
 *     audit, usage, discovery, compliance) require a console-session token with a sufficient role
 *     instead of a machine API key. The AI/proxy endpoints always stay API-key authenticated.
 */
@ConfigurationProperties(prefix = "auvex.rbac")
public record RbacProperties(boolean enabled) {}

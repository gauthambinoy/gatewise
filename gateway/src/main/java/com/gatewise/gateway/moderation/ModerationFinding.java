package com.gatewise.gateway.moderation;

/**
 * A content-safety hit.
 *
 * @param category the safety category that fired
 * @param evidence the matched snippet (from already-redacted text — never raw secrets)
 */
public record ModerationFinding(ModerationCategory category, String evidence) {}

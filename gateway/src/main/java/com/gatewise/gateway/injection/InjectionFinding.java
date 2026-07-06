package com.gatewise.gateway.injection;

/**
 * A prompt-injection hit.
 *
 * @param rule the rule that fired (its class/short name)
 * @param category the kind of attack (e.g. {@code instruction_override}, {@code jailbreak})
 * @param evidence the matched snippet (already from redacted text — never raw secrets)
 */
public record InjectionFinding(String rule, String category, String evidence) {}

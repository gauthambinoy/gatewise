package com.auvex.gateway.egress;

/**
 * What the governance pipeline decided about an intercepted request.
 *
 * <p>Either it's allowed — in which case {@code forwardBody} is the (possibly redacted) body to
 * send on to the real provider — or it's blocked, with a human-readable {@code blockReason} the
 * proxy returns to the client instead of forwarding.
 *
 * @param allowed whether the request may be forwarded
 * @param forwardBody the body to forward (only meaningful when allowed)
 * @param blockReason why it was blocked (only meaningful when not allowed)
 */
public record GovernanceDecision(boolean allowed, byte[] forwardBody, String blockReason) {

  static GovernanceDecision allow(byte[] forwardBody) {
    return new GovernanceDecision(true, forwardBody, null);
  }

  static GovernanceDecision block(String blockReason) {
    return new GovernanceDecision(false, null, blockReason);
  }
}

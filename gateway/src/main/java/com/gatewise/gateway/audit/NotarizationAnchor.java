package com.gatewise.gateway.audit;

import java.time.Instant;
import java.util.UUID;

/**
 * A point-in-time anchor of a tenant's audit chain, for external notarization.
 *
 * <p>The hash chain already makes the audit log tamper-evident to anyone who holds an earlier copy
 * of it. Publishing the current head hash to an independent timestamping/notary service closes the
 * last gap: a dated, third-party record that the chain was in exactly this state at this time, so
 * an operator can later prove the log wasn't rewritten wholesale after the fact.
 *
 * @param tenantId the tenant whose chain this anchors
 * @param headEntryId id of the most recent entry, or null if the chain is empty
 * @param headHash the head entry's hash (the genesis hash when the chain is empty)
 * @param generatedAt when this anchor was produced
 * @param chainIntact whether verification found the chain intact at this moment
 */
public record NotarizationAnchor(
    UUID tenantId, Long headEntryId, String headHash, Instant generatedAt, boolean chainIntact) {}

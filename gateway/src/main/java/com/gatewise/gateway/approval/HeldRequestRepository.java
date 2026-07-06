package com.gatewise.gateway.approval;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for the human-in-the-loop approval queue. */
public interface HeldRequestRepository extends JpaRepository<HeldRequest, UUID> {

  /** Pending holds for a tenant, oldest first (the reviewer's queue). */
  List<HeldRequest> findByTenantIdAndStatusOrderByCreatedAtAsc(UUID tenantId, String status);

  /** The most recent hold with a given status for a tenant + prompt (e.g. an approved prompt). */
  Optional<HeldRequest> findFirstByTenantIdAndPromptHashAndStatusOrderByCreatedAtDesc(
      UUID tenantId, String promptHash, String status);

  /** A single hold scoped to its tenant (so one tenant can't act on another's). */
  Optional<HeldRequest> findByIdAndTenantId(UUID id, UUID tenantId);
}

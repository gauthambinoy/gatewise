package com.auvex.gateway.member;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for console members. Every lookup is tenant-scoped. */
public interface MemberRepository extends JpaRepository<Member, UUID> {

  /** A tenant's members, for the list endpoint. */
  List<Member> findByTenantIdOrderByCreatedAtAsc(UUID tenantId);

  /** One member, only if it belongs to the tenant (a miss is a 404, not a leak). */
  Optional<Member> findByIdAndTenantId(UUID id, UUID tenantId);

  /** Whether the tenant already has a member with this email (uniqueness check). */
  boolean existsByTenantIdAndEmail(UUID tenantId, String email);

  /** A tenant's member by email, for console sign-in. */
  Optional<Member> findByTenantIdAndEmail(UUID tenantId, String email);

  /** A tenant's member by email, case-insensitively (SCIM userName lookups aren't case-exact). */
  Optional<Member> findByTenantIdAndEmailIgnoreCase(UUID tenantId, String email);

  /** The first member with a given role (e.g. an owner), for demo sign-in. */
  Optional<Member> findFirstByTenantIdAndRole(UUID tenantId, String role);
}

package com.gatewise.gateway.web;

import com.gatewise.gateway.member.Member;
import com.gatewise.gateway.member.MemberRepository;
import com.gatewise.gateway.member.Role;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The application service behind the member CRUD API.
 *
 * <p>Every operation is scoped to a tenant id, and single-member lookups go through {@code
 * findByIdAndTenantId}, so one tenant can never read or change another's members.
 */
@Component
public class MemberAdminService {

  private final MemberRepository members;

  public MemberAdminService(MemberRepository members) {
    this.members = members;
  }

  public List<Member> list(UUID tenantId) {
    return members.findByTenantIdOrderByCreatedAtAsc(tenantId);
  }

  public Member create(UUID tenantId, MemberRequest request) {
    if (members.existsByTenantIdAndEmail(tenantId, request.email())) {
      throw new InvalidRequestException("A member with that email already exists.");
    }
    Member member =
        new Member(
            tenantId,
            request.email(),
            request.name(),
            Role.from(request.role()).value(),
            statusOf(request));
    return members.save(member);
  }

  public Member get(UUID tenantId, UUID id) {
    return members
        .findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new NotFoundException("Member " + id + " not found."));
  }

  public Member update(UUID tenantId, UUID id, MemberRequest request) {
    Member member = get(tenantId, id);
    member.update(request.name(), Role.from(request.role()).value(), statusOf(request));
    return members.save(member);
  }

  public void delete(UUID tenantId, UUID id) {
    members.delete(get(tenantId, id));
  }

  private static String statusOf(MemberRequest request) {
    return "active".equalsIgnoreCase(request.status()) ? "active" : "invited";
  }
}

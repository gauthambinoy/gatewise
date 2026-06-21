package com.auvex.gateway.web;

import com.auvex.gateway.member.Member;
import java.time.OffsetDateTime;
import java.util.UUID;

/** The public representation of a console member. */
public record MemberView(
    UUID id, String email, String name, String role, String status, OffsetDateTime createdAt) {

  /** Projects a stored member into its API view. */
  public static MemberView of(Member member) {
    return new MemberView(
        member.getId(),
        member.getEmail(),
        member.getName(),
        member.getRole(),
        member.getStatus(),
        member.getCreatedAt());
  }
}

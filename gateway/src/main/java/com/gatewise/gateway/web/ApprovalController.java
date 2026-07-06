package com.gatewise.gateway.web;

import com.gatewise.gateway.approval.HeldRequest;
import com.gatewise.gateway.approval.HeldRequestRepository;
import com.gatewise.gateway.auth.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The reviewer's side of human-in-the-loop approval: list the pending queue and approve or reject a
 * held call. Approving a held prompt lets an identical retry through the gateway.
 */
@RestController
@RequestMapping("/v1/approvals")
public class ApprovalController {

  private final HeldRequestRepository held;

  public ApprovalController(HeldRequestRepository held) {
    this.held = held;
  }

  /** The tenant's pending approval queue, oldest first. */
  @GetMapping
  public List<HeldView> pending() {
    UUID tenantId = TenantContext.require().tenantId();
    return held.findByTenantIdAndStatusOrderByCreatedAtAsc(tenantId, "pending").stream()
        .map(HeldView::of)
        .toList();
  }

  /** Approve or reject one held call. */
  @PostMapping("/{id}/decision")
  public HeldView decide(@PathVariable UUID id, @Valid @RequestBody DecisionRequest request) {
    UUID tenantId = TenantContext.require().tenantId();
    String decision = request.decision().toLowerCase(Locale.ROOT);
    String status =
        switch (decision) {
          case "approve", "approved" -> "approved";
          case "reject", "rejected" -> "rejected";
          default -> throw new InvalidRequestException("decision must be 'approve' or 'reject'.");
        };

    HeldRequest entry =
        held.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new NotFoundException("No such held request."));
    if (!"pending".equals(entry.getStatus())) {
      throw new InvalidRequestException("This request has already been decided.");
    }
    entry.decide(status, request.decidedBy(), OffsetDateTime.now(ZoneOffset.UTC));
    return HeldView.of(held.save(entry));
  }

  /** A held request as returned to the reviewer. */
  public record HeldView(
      UUID id,
      String actor,
      String model,
      String reason,
      String promptRedacted,
      String status,
      OffsetDateTime createdAt,
      String decidedBy,
      OffsetDateTime decidedAt) {

    static HeldView of(HeldRequest h) {
      return new HeldView(
          h.getId(),
          h.getActor(),
          h.getModel(),
          h.getReason(),
          h.getPromptRedacted(),
          h.getStatus(),
          h.getCreatedAt(),
          h.getDecidedBy(),
          h.getDecidedAt());
    }
  }

  /** A reviewer's decision on a held call. */
  public record DecisionRequest(@NotBlank String decision, String decidedBy) {}
}

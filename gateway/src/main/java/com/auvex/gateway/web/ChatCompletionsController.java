package com.auvex.gateway.web;

import com.auvex.gateway.audit.AuditEntry;
import com.auvex.gateway.audit.AuditSink;
import com.auvex.gateway.audit.Verdict;
import com.auvex.gateway.auth.TenantContext;
import com.auvex.gateway.budget.BudgetService;
import com.auvex.gateway.cache.ResponseCache;
import com.auvex.gateway.policy.Decision;
import com.auvex.gateway.policy.EvaluationContext;
import com.auvex.gateway.policy.PolicyDeniedException;
import com.auvex.gateway.policy.PolicyEnforcement;
import com.auvex.gateway.proxy.CachedResponse;
import com.auvex.gateway.proxy.UpstreamProxy;
import com.auvex.gateway.redaction.Match;
import com.auvex.gateway.redaction.PromptRedactor;
import com.auvex.gateway.routing.ModelRouter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The gateway hot path: an OpenAI-compatible chat-completions endpoint.
 *
 * <p>Each request runs the full pipeline — validate, route the model alias, redact the prompt,
 * check the tenant's policy, and write an immutable audit record — before it's forwarded. A policy
 * denial stops it with a 403; the provider only ever sees an allowed, already-redacted request, and
 * every decision is recorded either way.
 */
@RestController
@RequestMapping("/v1")
public class ChatCompletionsController {

  private final UpstreamProxy proxy;
  private final ModelRouter router;
  private final PromptRedactor redactor;
  private final PolicyEnforcement policy;
  private final AuditSink auditSink;
  private final BudgetService budget;
  private final ResponseCache cache;
  private final ObjectMapper objectMapper;

  public ChatCompletionsController(
      UpstreamProxy proxy,
      ModelRouter router,
      PromptRedactor redactor,
      PolicyEnforcement policy,
      AuditSink auditSink,
      BudgetService budget,
      ResponseCache cache,
      ObjectMapper objectMapper) {
    this.proxy = proxy;
    this.router = router;
    this.redactor = redactor;
    this.policy = policy;
    this.auditSink = auditSink;
    this.budget = budget;
    this.cache = cache;
    this.objectMapper = objectMapper;
  }

  /** Accepts a chat-completions request and relays the provider's response to the caller. */
  @PostMapping("/chat/completions")
  public void chatCompletions(@RequestBody JsonNode body, HttpServletResponse response)
      throws IOException {
    validate(body);

    // Resolve the client's alias to a real provider model (the routing table is the allow-list).
    String providerModel = router.resolve(body.get("model").asText());
    ((ObjectNode) body).put("model", providerModel);

    // Mask sensitive data out of the prompt, and learn which data types it contained.
    List<Match> found = redactor.redactInPlace(body);

    // Enforce the tenant's policy; a denial is recorded, then stops the request.
    EvaluationContext ctx = contextFor(body, providerModel, found);
    Decision decision = policy.evaluate(ctx);
    if (!decision.allowed()) {
      auditSink.record(auditEntry(ctx, providerModel, Verdict.BLOCKED, body));
      throw new PolicyDeniedException(decision.reason());
    }

    // Enforce the tenant's call budget before doing any upstream work.
    if (!budget.allows(ctx.tenantId())) {
      throw new BudgetExceededException("Call budget exceeded for this tenant.");
    }

    // Record the allowed call, then forward it.
    Verdict verdict = found.isEmpty() ? Verdict.ALLOWED : Verdict.REDACTED;
    auditSink.record(auditEntry(ctx, providerModel, verdict, body));

    byte[] forwarded = objectMapper.writeValueAsBytes(body);
    if (body.path("stream").asBoolean(false)) {
      proxy.relay(forwarded, response); // streaming: straight through, no cache/failover
    } else {
      serveBuffered(ctx.tenantId(), forwarded, response);
    }
  }

  // Non-streaming path: serve from cache if present, else fetch (with failover) and cache a
  // success.
  private void serveBuffered(UUID tenantId, byte[] forwarded, HttpServletResponse response)
      throws IOException {
    CachedResponse result;
    if (cache.enabled()) {
      String key = cache.keyFor(tenantId, forwarded);
      result = cache.get(key);
      if (result == null) {
        result = proxy.fetch(forwarded);
        if (result.isSuccessful()) {
          cache.put(key, result);
        }
      }
    } else {
      result = proxy.fetch(forwarded);
    }
    response.setStatus(result.status());
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(result.contentType());
    response.getOutputStream().write(result.body().getBytes(StandardCharsets.UTF_8));
  }

  // Build the policy evaluation context from the tenant, model, caller and detected data types.
  private static EvaluationContext contextFor(
      JsonNode body, String providerModel, List<Match> found) {
    Set<String> dataTypes =
        found.stream()
            .map(match -> match.type().name().toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
    String actor =
        body.hasNonNull("user") && body.get("user").isTextual()
            ? body.get("user").asText()
            : "unknown";
    UUID tenantId = TenantContext.require().tenantId();
    return new EvaluationContext(tenantId, providerModel, actor, dataTypes);
  }

  // Builds the audit record for a request with a given verdict.
  private static AuditEntry auditEntry(
      EvaluationContext ctx, String model, Verdict verdict, JsonNode body) {
    return new AuditEntry(
        ctx.tenantId(),
        UUID.randomUUID(),
        ctx.actor(),
        model,
        verdict,
        redactedPrompt(body),
        null,
        Instant.now());
  }

  // The already-redacted prompt text, joined across messages, as stored in the audit log.
  private static String redactedPrompt(JsonNode body) {
    StringBuilder sb = new StringBuilder();
    for (JsonNode message : body.get("messages")) {
      if (sb.length() > 0) {
        sb.append('\n');
      }
      sb.append(message.path("content").asText(""));
    }
    return sb.toString();
  }

  // Reject obvious shape errors early with a clear message; the provider never sees them.
  private void validate(JsonNode body) {
    if (body == null || !body.isObject()) {
      throw new InvalidRequestException("Request body must be a JSON object.");
    }
    JsonNode model = body.get("model");
    if (model == null || !model.isTextual() || model.asText().isBlank()) {
      throw new InvalidRequestException("'model' is required and must be a non-empty string.");
    }
    JsonNode messages = body.get("messages");
    if (messages == null || !messages.isArray() || messages.isEmpty()) {
      throw new InvalidRequestException("'messages' is required and must be a non-empty array.");
    }
  }
}

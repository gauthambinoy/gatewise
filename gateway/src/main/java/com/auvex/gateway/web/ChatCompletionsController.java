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
import com.auvex.gateway.pricing.CostCalculator;
import com.auvex.gateway.pricing.TokenUsage;
import com.auvex.gateway.pricing.UsageExtractor;
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
import java.math.BigDecimal;
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
 * check the tenant's policy and budget — before it's forwarded. A policy denial stops it with a
 * 403; the provider only ever sees an allowed, already-redacted request. Every decision is recorded
 * in the audit log, and for completed non-streaming calls the token usage and cost are recorded
 * too.
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
  private final UsageExtractor usageExtractor;
  private final CostCalculator costCalculator;
  private final ObjectMapper objectMapper;

  public ChatCompletionsController(
      UpstreamProxy proxy,
      ModelRouter router,
      PromptRedactor redactor,
      PolicyEnforcement policy,
      AuditSink auditSink,
      BudgetService budget,
      ResponseCache cache,
      UsageExtractor usageExtractor,
      CostCalculator costCalculator,
      ObjectMapper objectMapper) {
    this.proxy = proxy;
    this.router = router;
    this.redactor = redactor;
    this.policy = policy;
    this.auditSink = auditSink;
    this.budget = budget;
    this.cache = cache;
    this.usageExtractor = usageExtractor;
    this.costCalculator = costCalculator;
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
      auditSink.record(auditEntry(ctx, providerModel, Verdict.BLOCKED, body, null, null, null));
      throw new PolicyDeniedException(decision.reason());
    }

    // Enforce the tenant's call budget before doing any upstream work.
    if (!budget.allows(ctx.tenantId())) {
      throw new BudgetExceededException("Call budget exceeded for this tenant.");
    }

    byte[] forwarded = objectMapper.writeValueAsBytes(body);
    Verdict verdict = found.isEmpty() ? Verdict.ALLOWED : Verdict.REDACTED;

    if (body.path("stream").asBoolean(false)) {
      // Streaming: token usage isn't available, so record without cost and stream straight through.
      auditSink.record(auditEntry(ctx, providerModel, verdict, body, null, null, null));
      proxy.relay(forwarded, response);
      return;
    }

    // Non-streaming: get the (possibly cached) response, price it, record it, then return it.
    CachedResponse result = fetchBuffered(ctx.tenantId(), forwarded);
    TokenUsage usage = usageExtractor.extract(result.body());
    auditSink.record(
        auditEntry(
            ctx,
            providerModel,
            verdict,
            body,
            usage == null ? null : usage.promptTokens(),
            usage == null ? null : usage.completionTokens(),
            costCalculator.cost(providerModel, usage)));
    writeResponse(response, result);
  }

  // Get the response for a non-streaming request: from cache if present, else fetch (with
  // failover).
  private CachedResponse fetchBuffered(UUID tenantId, byte[] forwarded) {
    if (!cache.enabled()) {
      return proxy.fetch(forwarded);
    }
    String key = cache.keyFor(tenantId, forwarded);
    CachedResponse cached = cache.get(key);
    if (cached != null) {
      return cached;
    }
    CachedResponse fresh = proxy.fetch(forwarded);
    if (fresh.isSuccessful()) {
      cache.put(key, fresh);
    }
    return fresh;
  }

  private static void writeResponse(HttpServletResponse response, CachedResponse cached)
      throws IOException {
    response.setStatus(cached.status());
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(cached.contentType());
    response.getOutputStream().write(cached.body().getBytes(StandardCharsets.UTF_8));
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

  // Builds the audit record for a request with a given verdict and (optional) token usage + cost.
  private static AuditEntry auditEntry(
      EvaluationContext ctx,
      String model,
      Verdict verdict,
      JsonNode body,
      Integer promptTokens,
      Integer completionTokens,
      BigDecimal cost) {
    return new AuditEntry(
        ctx.tenantId(),
        UUID.randomUUID(),
        ctx.actor(),
        model,
        verdict,
        redactedPrompt(body),
        null,
        Instant.now(),
        promptTokens,
        completionTokens,
        cost);
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

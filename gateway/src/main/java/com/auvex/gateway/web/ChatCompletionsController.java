package com.auvex.gateway.web;

import com.auvex.gateway.audit.AuditEntry;
import com.auvex.gateway.audit.AuditSink;
import com.auvex.gateway.audit.Verdict;
import com.auvex.gateway.auth.TenantContext;
import com.auvex.gateway.budget.BudgetService;
import com.auvex.gateway.cache.ResponseCache;
import com.auvex.gateway.config.InjectionProperties;
import com.auvex.gateway.config.RedactionProperties;
import com.auvex.gateway.config.TokenizationProperties;
import com.auvex.gateway.injection.InjectionFinding;
import com.auvex.gateway.injection.InjectionScanner;
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
import com.auvex.gateway.redaction.ResponseRedaction;
import com.auvex.gateway.redaction.ResponseRedactor;
import com.auvex.gateway.redaction.ReversibleRedaction;
import com.auvex.gateway.routing.ModelRouter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
  private final ResponseRedactor responseRedactor;
  private final RedactionProperties redaction;
  private final InjectionScanner injectionScanner;
  private final InjectionProperties injectionProperties;
  private final TokenizationProperties tokenization;
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
      ResponseRedactor responseRedactor,
      RedactionProperties redaction,
      InjectionScanner injectionScanner,
      InjectionProperties injectionProperties,
      TokenizationProperties tokenization,
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
    this.responseRedactor = responseRedactor;
    this.redaction = redaction;
    this.injectionScanner = injectionScanner;
    this.injectionProperties = injectionProperties;
    this.tokenization = tokenization;
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

    // Mask sensitive data out of the prompt, and learn which data types it contained. With
    // reversible tokenization on, the provider sees per-value tokens and the (non-streaming)
    // response is restored for the caller via the vault; otherwise it's a plain, non-reversible
    // mask and the vault is empty.
    Map<String, String> vault;
    List<Match> found;
    if (tokenization.enabled()) {
      ReversibleRedaction reversible = redactor.redactReversiblyInPlace(body);
      found = reversible.matches();
      vault = reversible.vault();
    } else {
      found = redactor.redactInPlace(body);
      vault = Map.of();
    }
    Map<String, Integer> redactionCounts = countByType(found);

    EvaluationContext ctx = contextFor(body, providerModel, found);

    // Screen for prompt injection / jailbreak; when blocking is on, a hit is recorded and stops it.
    if (injectionProperties.enabled() && injectionProperties.block()) {
      List<InjectionFinding> injections = injectionScanner.scan(redactedPrompt(body));
      if (!injections.isEmpty()) {
        auditSink.record(
            auditEntry(
                ctx,
                providerModel,
                Verdict.BLOCKED,
                body,
                null,
                null,
                null,
                null,
                redactionCounts));
        throw new PromptInjectionException(
            "Request blocked: possible prompt injection (" + injections.get(0).category() + ").");
      }
    }

    // Enforce the tenant's policy; a denial is recorded, then stops the request.
    Decision decision = policy.evaluate(ctx);
    if (!decision.allowed()) {
      auditSink.record(
          auditEntry(
              ctx, providerModel, Verdict.BLOCKED, body, null, null, null, null, redactionCounts));
      throw new PolicyDeniedException(decision.reason());
    }

    // Enforce the tenant's call budget before doing any upstream work.
    if (!budget.allows(ctx.tenantId())) {
      throw new BudgetExceededException("Call budget exceeded for this tenant.");
    }

    byte[] forwarded = objectMapper.writeValueAsBytes(body);

    if (body.path("stream").asBoolean(false)) {
      // Streaming: no buffered response or token usage, so record the request-side decision and
      // stream straight through. (Capturing a streamed response is a later step.)
      Verdict verdict = found.isEmpty() ? Verdict.ALLOWED : Verdict.REDACTED;
      auditSink.record(
          auditEntry(ctx, providerModel, verdict, body, null, null, null, null, redactionCounts));
      proxy.relay(forwarded, response);
      return;
    }

    // Non-streaming: fetch, redact the response, price it, record it, then return it.
    CachedResponse upstream = fetchBuffered(ctx.tenantId(), forwarded);
    ResponseRedaction redactedResponse =
        redaction.enabled() ? responseRedactor.redact(upstream.body()) : null;

    List<Match> allFound = found;
    String responseRedacted = null;
    if (redactedResponse != null) {
      allFound = new ArrayList<>(found);
      allFound.addAll(redactedResponse.matches());
      responseRedacted = redactedResponse.redactedText();
    }
    Verdict verdict = allFound.isEmpty() ? Verdict.ALLOWED : Verdict.REDACTED;
    TokenUsage usage = usageExtractor.extract(upstream.body());
    auditSink.record(
        auditEntry(
            ctx,
            providerModel,
            verdict,
            body,
            responseRedacted,
            usage == null ? null : usage.promptTokens(),
            usage == null ? null : usage.completionTokens(),
            costCalculator.cost(providerModel, usage),
            countByType(allFound)));

    // With enforcement on, strip the response's PII from what the caller receives too.
    CachedResponse toClient =
        redactedResponse != null && redaction.enforce()
            ? new CachedResponse(
                upstream.status(), upstream.contentType(), redactedResponse.maskedBody())
            : upstream;
    if (!vault.isEmpty()) {
      // Restore the caller's own values into the response — the provider only ever saw the tokens.
      toClient =
          new CachedResponse(
              toClient.status(), toClient.contentType(), restoreTokens(toClient.body(), vault));
    }
    writeResponse(response, toClient);
  }

  // Replace each reversible token in the response with its original value, for the caller only.
  private static String restoreTokens(String body, Map<String, String> vault) {
    String restored = body;
    for (Map.Entry<String, String> entry : vault.entrySet()) {
      restored = restored.replace(entry.getKey(), entry.getValue());
    }
    return restored;
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

  // Builds the audit record for a request with a given verdict, optional token usage + cost, and
  // the per-type redaction tallies.
  private static AuditEntry auditEntry(
      EvaluationContext ctx,
      String model,
      Verdict verdict,
      JsonNode body,
      String responseRedacted,
      Integer promptTokens,
      Integer completionTokens,
      BigDecimal cost,
      Map<String, Integer> redactionCounts) {
    return new AuditEntry(
        ctx.tenantId(),
        UUID.randomUUID(),
        ctx.actor(),
        model,
        verdict,
        redactedPrompt(body),
        responseRedacted,
        Instant.now(),
        promptTokens,
        completionTokens,
        cost,
        redactionCounts);
  }

  // Tally how many of each data type were redacted (e.g. {"email":2,"credit_card":1}).
  private static Map<String, Integer> countByType(List<Match> found) {
    Map<String, Integer> counts = new HashMap<>();
    for (Match match : found) {
      counts.merge(match.type().name().toLowerCase(Locale.ROOT), 1, Integer::sum);
    }
    return counts;
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

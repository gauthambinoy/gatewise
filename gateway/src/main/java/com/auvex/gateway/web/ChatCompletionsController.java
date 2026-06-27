package com.auvex.gateway.web;

import com.auvex.gateway.approval.ApprovalService;
import com.auvex.gateway.audit.AuditEntry;
import com.auvex.gateway.audit.AuditSink;
import com.auvex.gateway.audit.Verdict;
import com.auvex.gateway.auth.TenantContext;
import com.auvex.gateway.budget.BudgetService;
import com.auvex.gateway.cache.ResponseCache;
import com.auvex.gateway.cache.SemanticCache;
import com.auvex.gateway.config.InjectionProperties;
import com.auvex.gateway.config.RedactionProperties;
import com.auvex.gateway.config.TokenizationProperties;
import com.auvex.gateway.injection.InjectionFinding;
import com.auvex.gateway.injection.InjectionScanner;
import com.auvex.gateway.multimodal.ImageScanner;
import com.auvex.gateway.multimodal.MultimodalBlockedException;
import com.auvex.gateway.policy.Decision;
import com.auvex.gateway.policy.EvaluationContext;
import com.auvex.gateway.policy.PolicyDeniedException;
import com.auvex.gateway.policy.PolicyEnforcement;
import com.auvex.gateway.pricing.CostCalculator;
import com.auvex.gateway.pricing.TokenUsage;
import com.auvex.gateway.pricing.UsageExtractor;
import com.auvex.gateway.proxy.CachedResponse;
import com.auvex.gateway.proxy.StreamContentExtractor;
import com.auvex.gateway.proxy.UpstreamProxy;
import com.auvex.gateway.ratelimit.QuotaService;
import com.auvex.gateway.redaction.Match;
import com.auvex.gateway.redaction.PromptRedactor;
import com.auvex.gateway.redaction.ResponseRedaction;
import com.auvex.gateway.redaction.ResponseRedactor;
import com.auvex.gateway.redaction.ReversibleRedaction;
import com.auvex.gateway.residency.ResidencyEnforcer;
import com.auvex.gateway.routing.ModelRouter;
import com.auvex.gateway.routing.RoutingContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
  private final SemanticCache semanticCache;
  private final UsageExtractor usageExtractor;
  private final CostCalculator costCalculator;
  private final ResponseRedactor responseRedactor;
  private final RedactionProperties redaction;
  private final InjectionScanner injectionScanner;
  private final InjectionProperties injectionProperties;
  private final TokenizationProperties tokenization;
  private final StreamContentExtractor streamExtractor;
  private final QuotaService quotas;
  private final ApprovalService approvals;
  private final ImageScanner imageScanner;
  private final ResidencyEnforcer residency;
  private final ObjectMapper objectMapper;

  public ChatCompletionsController(
      UpstreamProxy proxy,
      ModelRouter router,
      PromptRedactor redactor,
      PolicyEnforcement policy,
      AuditSink auditSink,
      BudgetService budget,
      ResponseCache cache,
      SemanticCache semanticCache,
      UsageExtractor usageExtractor,
      CostCalculator costCalculator,
      ResponseRedactor responseRedactor,
      RedactionProperties redaction,
      InjectionScanner injectionScanner,
      InjectionProperties injectionProperties,
      TokenizationProperties tokenization,
      StreamContentExtractor streamExtractor,
      QuotaService quotas,
      ApprovalService approvals,
      ImageScanner imageScanner,
      ResidencyEnforcer residency,
      ObjectMapper objectMapper) {
    this.proxy = proxy;
    this.router = router;
    this.redactor = redactor;
    this.policy = policy;
    this.auditSink = auditSink;
    this.budget = budget;
    this.cache = cache;
    this.semanticCache = semanticCache;
    this.usageExtractor = usageExtractor;
    this.costCalculator = costCalculator;
    this.responseRedactor = responseRedactor;
    this.redaction = redaction;
    this.injectionScanner = injectionScanner;
    this.injectionProperties = injectionProperties;
    this.tokenization = tokenization;
    this.streamExtractor = streamExtractor;
    this.quotas = quotas;
    this.approvals = approvals;
    this.imageScanner = imageScanner;
    this.residency = residency;
    this.objectMapper = objectMapper;
  }

  /** Accepts a chat-completions request and relays the provider's response to the caller. */
  @PostMapping("/chat/completions")
  public void chatCompletions(@RequestBody JsonNode body, HttpServletResponse response)
      throws IOException {
    validate(body);

    // Resolve the client's alias to a real provider model (the routing table is the allow-list).
    // When smart routing is on, the strategy may pick among a candidate pool for this request.
    RoutingContext routingContext =
        new RoutingContext(TenantContext.require().tenantId(), estimatePromptTokens(body));
    String providerModel = router.resolve(body.get("model").asText(), routingContext);
    ((ObjectNode) body).put("model", providerModel);

    // Data residency: a region-pinned tenant may only reach models that run in its region.
    residency.enforce(TenantContext.require().tenantId(), providerModel);

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

    // Image governance: count any multimodal images, strip or block them per policy.
    ImageScanner.ScanResult imageScan = imageScanner.scan(body);
    if (imageScan.imageCount() > 0) {
      redactionCounts.merge("image", imageScan.imageCount(), Integer::sum);
    }
    if (imageScan.blocked()) {
      auditSink.record(
          auditEntry(
              ctx, providerModel, Verdict.BLOCKED, body, null, null, null, null, redactionCounts));
      throw new MultimodalBlockedException("Request blocked: image content is not permitted.");
    }

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

    // Human-in-the-loop: hold high-risk calls for approval before they're forwarded. An approved
    // prompt passes; a freshly-held one is recorded and returned as 202 pending.
    if (approvals.enabled()) {
      boolean injectionDetected = !injectionScanner.scan(redactedPrompt(body)).isEmpty();
      java.util.Optional<UUID> heldId =
          approvals.reviewIfNeeded(
              ctx.tenantId(),
              ctx.actor(),
              providerModel,
              redactedPrompt(body),
              injectionDetected,
              ctx.detectedDataTypes());
      if (heldId.isPresent()) {
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
        writeHeld(response, heldId.get());
        return;
      }
    }

    // Enforce the tenant's call budget before doing any upstream work.
    if (!budget.allows(ctx.tenantId())) {
      throw new BudgetExceededException("Call budget exceeded for this tenant.");
    }

    // Enforce per-caller and per-model daily quotas (off unless configured).
    quotas.check(ctx.tenantId(), ctx.actor(), providerModel);

    byte[] forwarded = objectMapper.writeValueAsBytes(body);

    if (body.path("stream").asBoolean(false)) {
      // Streaming: tee the response through to the caller, then capture and screen it. It's already
      // been sent, so response redaction is audit-only here — we can't unsend a leak, but we record
      // it (verdict + counts) so the streamed response is governed too, not a blind spot.
      byte[] streamed = proxy.relay(forwarded, response);
      String streamedContent =
          streamExtractor.extract(new String(streamed, StandardCharsets.UTF_8));
      ResponseRedaction redactedResponse =
          redaction.enabled() && !streamedContent.isEmpty()
              ? responseRedactor.redact(choicesEnvelope(streamedContent))
              : null;
      List<Match> streamFound = found;
      String responseRedacted = null;
      if (redactedResponse != null) {
        streamFound = new ArrayList<>(found);
        streamFound.addAll(redactedResponse.matches());
        responseRedacted = redactedResponse.redactedText();
      }
      Verdict verdict = streamFound.isEmpty() ? Verdict.ALLOWED : Verdict.REDACTED;
      auditSink.record(
          auditEntry(
              ctx,
              providerModel,
              verdict,
              body,
              responseRedacted,
              null,
              null,
              null,
              countByType(streamFound)));
      return;
    }

    // Non-streaming: fetch, redact the response, price it, record it, then return it.
    CachedResponse upstream = fetchBuffered(ctx.tenantId(), forwarded, redactedPrompt(body));
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

  // Wrap streamed assistant text in a minimal chat-completion envelope so the response redactor —
  // which reads choices[].message.content — can screen it.
  private String choicesEnvelope(String content) throws IOException {
    ObjectNode root = objectMapper.createObjectNode();
    ArrayNode choices = root.putArray("choices");
    choices.addObject().putObject("message").put("content", content);
    return objectMapper.writeValueAsString(root);
  }

  // Replace each reversible token in the response with its original value, for the caller only.
  private static String restoreTokens(String body, Map<String, String> vault) {
    String restored = body;
    for (Map.Entry<String, String> entry : vault.entrySet()) {
      restored = restored.replace(entry.getKey(), entry.getValue());
    }
    return restored;
  }

  // Get the response for a non-streaming request: exact-match cache, then semantic (near-duplicate)
  // cache, else fetch (with failover) and populate both caches.
  private CachedResponse fetchBuffered(UUID tenantId, byte[] forwarded, String promptText) {
    String key = cache.enabled() ? cache.keyFor(tenantId, forwarded) : null;
    if (key != null) {
      CachedResponse cached = cache.get(key);
      if (cached != null) {
        return cached;
      }
    }
    CachedResponse semantic = semanticCache.lookup(tenantId, promptText);
    if (semantic != null) {
      return semantic;
    }
    CachedResponse fresh = proxy.fetch(forwarded);
    if (fresh.isSuccessful()) {
      if (key != null) {
        cache.put(key, fresh);
      }
      semanticCache.store(tenantId, promptText, fresh);
    }
    return fresh;
  }

  // A 202 telling the caller the request is held for human approval, with its id.
  private void writeHeld(HttpServletResponse response, UUID approvalId) throws IOException {
    response.setStatus(HttpServletResponse.SC_ACCEPTED);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType("application/json");
    ObjectNode body = objectMapper.createObjectNode();
    body.put("status", "pending_approval");
    body.put("approval_id", approvalId.toString());
    body.put("message", "This request requires human approval before it can be sent.");
    response.getOutputStream().write(objectMapper.writeValueAsBytes(body));
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

  // A rough token estimate from the prompt size (~4 chars per token), used for cost-based routing.
  private static int estimatePromptTokens(JsonNode body) {
    int chars = 0;
    JsonNode messages = body.get("messages");
    if (messages != null && messages.isArray()) {
      for (JsonNode message : messages) {
        chars += message.path("content").asText("").length();
      }
    }
    return Math.max(1, chars / 4);
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

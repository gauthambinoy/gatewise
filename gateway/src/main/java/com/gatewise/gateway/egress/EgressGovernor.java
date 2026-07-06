package com.gatewise.gateway.egress;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatewise.gateway.audit.AuditEntry;
import com.gatewise.gateway.audit.AuditSink;
import com.gatewise.gateway.audit.Verdict;
import com.gatewise.gateway.config.EgressProperties;
import com.gatewise.gateway.config.InjectionProperties;
import com.gatewise.gateway.injection.InjectionScanner;
import com.gatewise.gateway.policy.Decision;
import com.gatewise.gateway.policy.EvaluationContext;
import com.gatewise.gateway.policy.PolicyEnforcement;
import com.gatewise.gateway.redaction.Match;
import com.gatewise.gateway.redaction.PromptRedactor;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Runs an intercepted request through the same governance the cooperative gateway path applies, so
 * traffic the egress proxy catches is treated identically: the prompt is redacted, screened for
 * injection, checked against the tenant's policy, and audited — before it reaches the provider.
 *
 * <p>This is deliberately the only place the egress proxy touches request content, and it reuses
 * the shared engines ({@link PromptRedactor}, {@link InjectionScanner}, {@link PolicyEnforcement},
 * {@link AuditSink}) rather than re-implementing them, so there is no second, drifting copy of the
 * rules. A non-JSON body (a GET, or a shape we don't recognise) carries no prompt to govern and is
 * forwarded unchanged. Tenant attribution comes from {@code gatewise.egress.tenant-id}, because an
 * intercepted call carries the app's provider key, not an GateWise key.
 */
@Component
@ConditionalOnProperty(prefix = "gatewise.egress", name = "enabled", havingValue = "true")
public class EgressGovernor {

  private final PromptRedactor redactor;
  private final InjectionScanner injectionScanner;
  private final InjectionProperties injectionProperties;
  private final PolicyEnforcement policy;
  private final AuditSink auditSink;
  private final EgressProperties properties;
  private final ObjectMapper objectMapper;

  public EgressGovernor(
      PromptRedactor redactor,
      InjectionScanner injectionScanner,
      InjectionProperties injectionProperties,
      PolicyEnforcement policy,
      AuditSink auditSink,
      EgressProperties properties,
      ObjectMapper objectMapper) {
    this.redactor = redactor;
    this.injectionScanner = injectionScanner;
    this.injectionProperties = injectionProperties;
    this.policy = policy;
    this.auditSink = auditSink;
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  /** Governs one intercepted request and returns whether (and with what body) to forward it. */
  public GovernanceDecision govern(InterceptedRequest request) {
    JsonNode body = parse(request.body());
    if (body == null || !body.isObject()) {
      // Nothing we can read as a prompt; forward the bytes untouched (TLS is already terminated).
      return GovernanceDecision.allow(request.body());
    }

    // Mask sensitive data in place — the provider only ever sees the redacted prompt.
    List<Match> found = redactor.redactInPlace(body);
    String prompt = promptText(body);
    String model = body.path("model").asText("egress/" + request.host());
    Set<String> dataTypes =
        found.stream()
            .map(match -> match.type().name().toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
    EvaluationContext context =
        new EvaluationContext(
            properties.resolveTenantId(), model, "egress:" + request.host(), dataTypes);
    Map<String, Integer> redactionCounts = countByType(found);

    // Prompt-injection screening: when blocking is on, a hit is recorded and stops the request.
    if (injectionProperties.enabled()
        && injectionProperties.block()
        && !injectionScanner.scan(prompt).isEmpty()) {
      audit(context, model, Verdict.BLOCKED, prompt, redactionCounts);
      return GovernanceDecision.block("possible prompt injection");
    }

    // Policy: a denial is blocked only under mandatory routing; otherwise it's observed and passed.
    Decision decision = policy.evaluate(context);
    if (!decision.allowed() && properties.blockUncovered()) {
      audit(context, model, Verdict.BLOCKED, prompt, redactionCounts);
      return GovernanceDecision.block(decision.reason());
    }

    Verdict verdict = found.isEmpty() ? Verdict.ALLOWED : Verdict.REDACTED;
    audit(context, model, verdict, prompt, redactionCounts);
    // Re-serialise only when redaction actually changed the body, to avoid altering it needlessly.
    byte[] forwardBody = found.isEmpty() ? request.body() : serialize(body);
    return GovernanceDecision.allow(forwardBody);
  }

  private JsonNode parse(byte[] body) {
    if (body == null || body.length == 0) {
      return null;
    }
    try {
      return objectMapper.readTree(body);
    } catch (IOException e) {
      return null;
    }
  }

  private byte[] serialize(JsonNode body) {
    try {
      return objectMapper.writeValueAsBytes(body);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to re-serialise the redacted request body", e);
    }
  }

  private void audit(
      EvaluationContext context,
      String model,
      Verdict verdict,
      String prompt,
      Map<String, Integer> redactionCounts) {
    auditSink.record(
        new AuditEntry(
            context.tenantId(),
            UUID.randomUUID(),
            context.actor(),
            model,
            verdict,
            prompt,
            null,
            Instant.now(),
            null,
            null,
            null,
            redactionCounts));
  }

  // The already-redacted prompt text, joined across messages, for the audit record + injection
  // scan.
  private static String promptText(JsonNode body) {
    JsonNode messages = body.get("messages");
    if (messages == null || !messages.isArray()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (JsonNode message : messages) {
      JsonNode content = message.path("content");
      if (content.isTextual()) {
        append(sb, content.asText());
      } else if (content.isArray()) {
        for (JsonNode part : content) {
          if ("text".equals(part.path("type").asText())) {
            append(sb, part.path("text").asText(""));
          }
        }
      }
    }
    return sb.toString();
  }

  private static void append(StringBuilder sb, String text) {
    if (sb.length() > 0) {
      sb.append('\n');
    }
    sb.append(text);
  }

  private static Map<String, Integer> countByType(List<Match> found) {
    Map<String, Integer> counts = new HashMap<>();
    for (Match match : found) {
      counts.merge(match.type().name().toLowerCase(Locale.ROOT), 1, Integer::sum);
    }
    return counts;
  }
}

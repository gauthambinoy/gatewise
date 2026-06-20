package com.auvex.gateway.web;

import com.auvex.gateway.auth.TenantContext;
import com.auvex.gateway.policy.Decision;
import com.auvex.gateway.policy.EvaluationContext;
import com.auvex.gateway.policy.PolicyDeniedException;
import com.auvex.gateway.policy.PolicyEnforcement;
import com.auvex.gateway.proxy.UpstreamProxy;
import com.auvex.gateway.redaction.Match;
import com.auvex.gateway.redaction.PromptRedactor;
import com.auvex.gateway.routing.ModelRouter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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
 * <p>Each request runs the full pipeline — validate, route the model alias, redact the prompt, then
 * check the tenant's policy — before it's forwarded. A policy denial stops it here with a 403; the
 * provider only ever sees an allowed, already-redacted request.
 */
@RestController
@RequestMapping("/v1")
public class ChatCompletionsController {

  private final UpstreamProxy proxy;
  private final ModelRouter router;
  private final PromptRedactor redactor;
  private final PolicyEnforcement policy;
  private final ObjectMapper objectMapper;

  public ChatCompletionsController(
      UpstreamProxy proxy,
      ModelRouter router,
      PromptRedactor redactor,
      PolicyEnforcement policy,
      ObjectMapper objectMapper) {
    this.proxy = proxy;
    this.router = router;
    this.redactor = redactor;
    this.policy = policy;
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

    // Enforce the tenant's policy; a denial never reaches the provider.
    Decision decision = policy.evaluate(contextFor(body, providerModel, found));
    if (!decision.allowed()) {
      throw new PolicyDeniedException(decision.reason());
    }

    proxy.relay(objectMapper.writeValueAsBytes(body), response);
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

package com.gatewise.gateway.web;

import com.gatewise.gateway.multimodal.MultimodalBlockedException;
import com.gatewise.gateway.oidc.OidcException;
import com.gatewise.gateway.policy.PolicyDeniedException;
import com.gatewise.gateway.proxy.UpstreamUnavailableException;
import com.gatewise.gateway.ratelimit.QuotaExceededException;
import com.gatewise.gateway.residency.DataResidencyException;
import com.gatewise.gateway.routing.ModelNotAllowedException;
import com.gatewise.gateway.saml.SamlException;
import com.gatewise.gateway.scim.ScimException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates gateway errors into clean, OpenAI-style JSON instead of leaking stack traces.
 *
 * <p>Every response is {@code {"error": {"message", "type"}}}, the shape OpenAI-compatible clients
 * already understand, with an HTTP status that matches what went wrong.
 */
@RestControllerAdvice
public class GatewayExceptionHandler {

  private static final java.util.List<String> SCIM_ERROR_SCHEMAS =
      java.util.List.of("urn:ietf:params:scim:api:messages:2.0:Error");

  /** A syntactically valid body that isn't a usable chat-completions payload → 400. */
  @ExceptionHandler(InvalidRequestException.class)
  public ResponseEntity<Map<String, Object>> handleInvalid(InvalidRequestException e) {
    return error(HttpStatus.BAD_REQUEST, e.getMessage(), "invalid_request_error");
  }

  /** Body that couldn't be parsed as JSON at all → 400. */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException e) {
    return error(
        HttpStatus.BAD_REQUEST, "Request body is not valid JSON.", "invalid_request_error");
  }

  /** A request for a model alias the gateway isn't configured to allow → 400. */
  @ExceptionHandler(ModelNotAllowedException.class)
  public ResponseEntity<Map<String, Object>> handleModelNotAllowed(ModelNotAllowedException e) {
    return error(HttpStatus.BAD_REQUEST, e.getMessage(), "invalid_request_error");
  }

  /** A request blocked by the tenant's policy → 403. */
  @ExceptionHandler(PolicyDeniedException.class)
  public ResponseEntity<Map<String, Object>> handlePolicyDenied(PolicyDeniedException e) {
    return error(HttpStatus.FORBIDDEN, e.getMessage(), "policy_violation");
  }

  /** A request blocked because the prompt looks like an injection / jailbreak → 403. */
  @ExceptionHandler(PromptInjectionException.class)
  public ResponseEntity<Map<String, Object>> handleInjection(PromptInjectionException e) {
    return error(HttpStatus.FORBIDDEN, e.getMessage(), "prompt_injection");
  }

  /** A request blocked because the model is outside the tenant's data-residency region → 403. */
  @ExceptionHandler(DataResidencyException.class)
  public ResponseEntity<Map<String, Object>> handleResidency(DataResidencyException e) {
    return error(HttpStatus.FORBIDDEN, e.getMessage(), "data_residency_violation");
  }

  /** A request blocked because it carries image content the policy forbids → 403. */
  @ExceptionHandler(MultimodalBlockedException.class)
  public ResponseEntity<Map<String, Object>> handleMultimodalBlocked(MultimodalBlockedException e) {
    return error(HttpStatus.FORBIDDEN, e.getMessage(), "content_blocked");
  }

  /** A tenant that has used up its call budget → 429. */
  @ExceptionHandler(BudgetExceededException.class)
  public ResponseEntity<Map<String, Object>> handleBudget(BudgetExceededException e) {
    return error(HttpStatus.TOO_MANY_REQUESTS, e.getMessage(), "rate_limit_exceeded");
  }

  /** A caller or model that has hit its daily quota → 429. */
  @ExceptionHandler(QuotaExceededException.class)
  public ResponseEntity<Map<String, Object>> handleQuota(QuotaExceededException e) {
    return error(HttpStatus.TOO_MANY_REQUESTS, e.getMessage(), "quota_exceeded");
  }

  /** Upstream provider unreachable or too slow → 504. */
  @ExceptionHandler(UpstreamUnavailableException.class)
  public ResponseEntity<Map<String, Object>> handleUpstream(UpstreamUnavailableException e) {
    return error(HttpStatus.GATEWAY_TIMEOUT, e.getMessage(), "upstream_error");
  }

  /** A resource that doesn't exist for this tenant → 404. */
  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException e) {
    return error(HttpStatus.NOT_FOUND, e.getMessage(), "not_found");
  }

  /** An OIDC sign-in that failed verification (bad state, token or id_token) → 401. */
  @ExceptionHandler(OidcException.class)
  public ResponseEntity<Map<String, Object>> handleOidc(OidcException e) {
    return error(HttpStatus.UNAUTHORIZED, e.getMessage(), "authentication_error");
  }

  /** A SAML sign-in that failed verification (bad signature, audience, time window) → 401. */
  @ExceptionHandler(SamlException.class)
  public ResponseEntity<Map<String, Object>> handleSaml(SamlException e) {
    return error(HttpStatus.UNAUTHORIZED, e.getMessage(), "authentication_error");
  }

  /** A SCIM request that failed — returned in the SCIM 2.0 error envelope, not the gateway one. */
  @ExceptionHandler(ScimException.class)
  public ResponseEntity<Map<String, Object>> handleScim(ScimException e) {
    Map<String, Object> body = new java.util.LinkedHashMap<>();
    body.put("schemas", SCIM_ERROR_SCHEMAS);
    body.put("detail", e.getMessage());
    body.put("status", String.valueOf(e.status()));
    if (e.scimType() != null) {
      body.put("scimType", e.scimType());
    }
    return ResponseEntity.status(e.status()).body(body);
  }

  /** Bean-validation failure on a request body → 400, naming the first bad field. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
    String message =
        e.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
            .orElse("Invalid request.");
    return error(HttpStatus.BAD_REQUEST, message, "invalid_request_error");
  }

  private ResponseEntity<Map<String, Object>> error(
      HttpStatus status, String message, String type) {
    return ResponseEntity.status(status)
        .body(Map.of("error", Map.of("message", message, "type", type)));
  }
}

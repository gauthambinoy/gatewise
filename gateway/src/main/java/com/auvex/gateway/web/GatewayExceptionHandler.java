package com.auvex.gateway.web;

import com.auvex.gateway.policy.PolicyDeniedException;
import com.auvex.gateway.proxy.UpstreamUnavailableException;
import com.auvex.gateway.routing.ModelNotAllowedException;
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

  /** A tenant that has used up its call budget → 429. */
  @ExceptionHandler(BudgetExceededException.class)
  public ResponseEntity<Map<String, Object>> handleBudget(BudgetExceededException e) {
    return error(HttpStatus.TOO_MANY_REQUESTS, e.getMessage(), "rate_limit_exceeded");
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

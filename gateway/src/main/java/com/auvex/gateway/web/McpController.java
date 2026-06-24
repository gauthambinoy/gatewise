package com.auvex.gateway.web;

import com.auvex.gateway.injection.InjectionFinding;
import com.auvex.gateway.injection.InjectionScanner;
import com.auvex.gateway.moderation.ContentModerationScanner;
import com.auvex.gateway.redaction.Match;
import com.auvex.gateway.redaction.RedactionEngine;
import com.auvex.gateway.redaction.RedactionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Locale;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes Auvex's governance engines as a Model Context Protocol (MCP) server over JSON-RPC 2.0, so
 * MCP-aware agents and tools can redact and screen text through the gateway. It speaks the core MCP
 * methods — {@code initialize}, {@code tools/list}, {@code tools/call} — and offers three tools:
 * {@code redact}, {@code moderate} and {@code scan_injection}, each reusing the very same engines
 * as the {@code /v1} endpoints. It lives under {@code /v1} so every call is authenticated to a
 * tenant by the standard API-key filter.
 */
@RestController
@RequestMapping("/v1")
public class McpController {

  private static final String PROTOCOL_VERSION = "2024-11-05";

  private final RedactionEngine redaction;
  private final InjectionScanner injectionScanner;
  private final ContentModerationScanner moderationScanner;
  private final ObjectMapper json;

  public McpController(
      RedactionEngine redaction,
      InjectionScanner injectionScanner,
      ContentModerationScanner moderationScanner,
      ObjectMapper json) {
    this.redaction = redaction;
    this.injectionScanner = injectionScanner;
    this.moderationScanner = moderationScanner;
    this.json = json;
  }

  /** Single JSON-RPC 2.0 entry point for the MCP server. */
  @PostMapping("/mcp")
  public JsonNode handle(@RequestBody JsonNode request) {
    String method = request.path("method").asText();
    JsonNode id = request.get("id");
    return switch (method) {
      case "initialize" -> ok(id, initializeResult());
      case "tools/list" -> ok(id, toolsList());
      case "tools/call" -> toolCall(id, request.path("params"));
      // A notification (no id) needs no real response; acknowledge gracefully.
      case "notifications/initialized" -> ok(id, json.createObjectNode());
      default -> error(id, -32601, "Method not found: " + method);
    };
  }

  private ObjectNode initializeResult() {
    ObjectNode result = json.createObjectNode();
    result.put("protocolVersion", PROTOCOL_VERSION);
    ObjectNode capabilities = json.createObjectNode();
    capabilities.set("tools", json.createObjectNode());
    result.set("capabilities", capabilities);
    ObjectNode serverInfo = json.createObjectNode();
    serverInfo.put("name", "auvex-governance");
    serverInfo.put("version", "1.0.0");
    result.set("serverInfo", serverInfo);
    return result;
  }

  private ObjectNode toolsList() {
    ArrayNode tools = json.createArrayNode();
    tools.add(tool("redact", "Mask PII and secrets in text, returning the redacted text."));
    tools.add(
        tool(
            "moderate",
            "Screen text for PII, secrets, prompt-injection and unsafe content categories."));
    tools.add(tool("scan_injection", "Detect prompt-injection / jailbreak attempts in text."));
    ObjectNode result = json.createObjectNode();
    result.set("tools", tools);
    return result;
  }

  // Every tool takes the same { text } argument; describe it with a JSON Schema.
  private ObjectNode tool(String name, String description) {
    ObjectNode schema = json.createObjectNode();
    schema.put("type", "object");
    ObjectNode properties = json.createObjectNode();
    ObjectNode text = json.createObjectNode();
    text.put("type", "string");
    text.put("description", "The text to process.");
    properties.set("text", text);
    schema.set("properties", properties);
    schema.set("required", json.createArrayNode().add("text"));

    ObjectNode tool = json.createObjectNode();
    tool.put("name", name);
    tool.put("description", description);
    tool.set("inputSchema", schema);
    return tool;
  }

  private JsonNode toolCall(JsonNode id, JsonNode params) {
    String name = params.path("name").asText();
    JsonNode arguments = params.path("arguments");
    JsonNode textNode = arguments.path("text");
    if (!textNode.isTextual()) {
      return error(id, -32602, "Invalid params: 'text' (string) is required.");
    }
    String text = textNode.asText();

    String resultText =
        switch (name) {
          case "redact" -> redactTool(text);
          case "moderate" -> moderateTool(text);
          case "scan_injection" -> injectionTool(text);
          default -> null;
        };
    if (resultText == null) {
      return error(id, -32602, "Unknown tool: " + name);
    }
    return ok(id, toolContent(resultText));
  }

  private String redactTool(String text) {
    RedactionResult result = redaction.redact(text);
    return result.masked();
  }

  private String moderateTool(String text) {
    ObjectNode out = json.createObjectNode();
    ObjectNode sensitive = json.createObjectNode();
    for (Match match : redaction.redact(text).matches()) {
      String type = match.type().name().toLowerCase(Locale.ROOT);
      sensitive.put(type, sensitive.path(type).asInt(0) + 1);
    }
    ArrayNode injection = json.createArrayNode();
    injectionScanner.scan(text).stream()
        .map(InjectionFinding::category)
        .distinct()
        .forEach(injection::add);
    ArrayNode moderation = json.createArrayNode();
    moderationScanner.categories(text).forEach(moderation::add);

    boolean flagged = sensitive.size() > 0 || injection.size() > 0 || moderation.size() > 0;
    out.put("flagged", flagged);
    out.set("sensitiveData", sensitive);
    out.set("injection", injection);
    out.set("moderation", moderation);
    return out.toString();
  }

  private String injectionTool(String text) {
    ArrayNode categories = json.createArrayNode();
    injectionScanner.scan(text).stream()
        .map(InjectionFinding::category)
        .distinct()
        .forEach(categories::add);
    return categories.toString();
  }

  // The MCP tools/call result shape: { content: [ { type: "text", text } ] }.
  private ObjectNode toolContent(String text) {
    ObjectNode content = json.createObjectNode();
    content.put("type", "text");
    content.put("text", text);
    ObjectNode result = json.createObjectNode();
    result.set("content", json.createArrayNode().add(content));
    return result;
  }

  private ObjectNode ok(JsonNode id, JsonNode result) {
    ObjectNode response = json.createObjectNode();
    response.put("jsonrpc", "2.0");
    response.set("id", id == null ? null : id);
    response.set("result", result);
    return response;
  }

  private ObjectNode error(JsonNode id, int code, String message) {
    ObjectNode error = json.createObjectNode();
    error.put("code", code);
    error.put("message", message);
    ObjectNode response = json.createObjectNode();
    response.put("jsonrpc", "2.0");
    response.set("id", id == null ? null : id);
    response.set("error", error);
    return response;
  }
}

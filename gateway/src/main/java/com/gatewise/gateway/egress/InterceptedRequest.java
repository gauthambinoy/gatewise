package com.gatewise.gateway.egress;

import java.util.List;

/**
 * One HTTP request read off an intercepted (TLS-terminated) connection.
 *
 * @param host the intercepted host (from the CONNECT line), e.g. {@code api.openai.com}
 * @param method the request method, e.g. {@code POST}
 * @param target the request target / path, e.g. {@code /v1/chat/completions}
 * @param version the HTTP version token, e.g. {@code HTTP/1.1}
 * @param headers the raw header lines, in order ({@code Name: value}), excluding the blank line
 * @param body the request body bytes (empty when there is none)
 */
public record InterceptedRequest(
    String host, String method, String target, String version, List<String> headers, byte[] body) {

  public InterceptedRequest {
    headers = List.copyOf(headers);
  }
}

package com.gatewise.gateway.egress;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies the small HTTP/1.1 reader/writer used to parse and relay intercepted traffic. */
class ProxyHttpTest {

  @Test
  void readsRequestLineHeadersAndContentLengthBody() throws Exception {
    String raw =
        "POST /v1/chat/completions HTTP/1.1\r\n"
            + "Host: api.openai.com\r\n"
            + "Content-Type: application/json\r\n"
            + "Content-Length: 13\r\n\r\n"
            + "{\"a\":\"hello\"}";

    InterceptedRequest request =
        ProxyHttp.readRequest(
            new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)), "api.openai.com");

    assertThat(request).isNotNull();
    assertThat(request.method()).isEqualTo("POST");
    assertThat(request.target()).isEqualTo("/v1/chat/completions");
    assertThat(request.version()).isEqualTo("HTTP/1.1");
    assertThat(request.host()).isEqualTo("api.openai.com");
    assertThat(request.headers()).contains("Host: api.openai.com");
    assertThat(new String(request.body(), StandardCharsets.UTF_8)).isEqualTo("{\"a\":\"hello\"}");
  }

  @Test
  void returnsNullAtEndOfStream() throws Exception {
    assertThat(ProxyHttp.readRequest(new ByteArrayInputStream(new byte[0]), "h")).isNull();
  }

  @Test
  void rewritesContentLengthAndDropsFramingHeaders() throws Exception {
    InterceptedRequest request =
        new InterceptedRequest(
            "api.openai.com",
            "POST",
            "/v1/x",
            "HTTP/1.1",
            List.of("Host: api.openai.com", "Content-Length: 999", "Transfer-Encoding: chunked"),
            new byte[0]);
    byte[] body = "{\"x\":1}".getBytes(StandardCharsets.UTF_8);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ProxyHttp.writeRequest(out, request, body);
    String written = out.toString(StandardCharsets.UTF_8);

    assertThat(written).startsWith("POST /v1/x HTTP/1.1\r\n");
    assertThat(written).contains("Host: api.openai.com\r\n");
    assertThat(written).contains("Content-Length: " + body.length + "\r\n");
    assertThat(written).doesNotContain("Content-Length: 999");
    assertThat(written).doesNotContain("Transfer-Encoding");
    assertThat(written).endsWith("\r\n\r\n{\"x\":1}");
  }

  @Test
  void relaysAFixedLengthResponseAndKeepsTheConnection() throws Exception {
    String response =
        "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 7\r\n\r\n{\"x\":1}";
    ByteArrayOutputStream client = new ByteArrayOutputStream();

    boolean reusable =
        ProxyHttp.relayResponse(
            new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8)), client);

    assertThat(reusable).isTrue();
    assertThat(client.toString(StandardCharsets.UTF_8)).isEqualTo(response);
  }

  @Test
  void aCloseDelimitedResponseEndsTheConnection() throws Exception {
    String response = "HTTP/1.1 200 OK\r\nConnection: close\r\n\r\nthe-body";
    ByteArrayOutputStream client = new ByteArrayOutputStream();

    boolean reusable =
        ProxyHttp.relayResponse(
            new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8)), client);

    assertThat(reusable).isFalse();
    assertThat(client.toString(StandardCharsets.UTF_8)).isEqualTo(response);
  }
}

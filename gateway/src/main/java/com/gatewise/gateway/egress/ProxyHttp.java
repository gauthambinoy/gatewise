package com.gatewise.gateway.egress;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * A minimal HTTP/1.1 reader/writer for the bytes flowing across an intercepted connection.
 *
 * <p>This is deliberately small: it parses just enough of a request to govern it (the request line,
 * the headers, and a {@code Content-Length}-framed body) and relays a response by its framing
 * ({@code Content-Length}, chunked, or close-delimited). It is not a general HTTP stack — chunked
 * request bodies, for instance, are out of scope (the AI APIs we intercept send length-framed
 * requests) and such a body simply isn't governed. Header bytes are ASCII; the body is opaque.
 */
final class ProxyHttp {

  private static final int BUFFER_SIZE = 8192;

  private ProxyHttp() {}

  /**
   * Reads one CRLF-terminated line as ASCII, without the line terminator; null at end of stream.
   */
  static String readLine(InputStream in) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    int b;
    while ((b = in.read()) != -1) {
      if (b == '\n') {
        byte[] bytes = buffer.toByteArray();
        int length = bytes.length;
        if (length > 0 && bytes[length - 1] == '\r') {
          length--;
        }
        return new String(bytes, 0, length, StandardCharsets.US_ASCII);
      }
      buffer.write(b);
    }
    return buffer.size() == 0 ? null : buffer.toString(StandardCharsets.US_ASCII);
  }

  /** Reads exactly {@code length} bytes (or fewer if the stream ends first). */
  static byte[] readFully(InputStream in, int length) throws IOException {
    byte[] body = new byte[length];
    int offset = 0;
    while (offset < length) {
      int read = in.read(body, offset, length - offset);
      if (read == -1) {
        break;
      }
      offset += read;
    }
    return offset == length ? body : Arrays.copyOf(body, offset);
  }

  /**
   * Reads one request from a (decrypted) stream, or null when the peer has closed the connection.
   */
  static InterceptedRequest readRequest(InputStream in, String host) throws IOException {
    String requestLine = readLine(in);
    if (requestLine == null || requestLine.isBlank()) {
      return null;
    }
    String[] parts = requestLine.split(" ", 3);
    String method = parts.length > 0 ? parts[0] : "";
    String target = parts.length > 1 ? parts[1] : "";
    String version = parts.length > 2 ? parts[2] : "HTTP/1.1";

    List<String> headers = new ArrayList<>();
    int contentLength = 0;
    String line;
    while ((line = readLine(in)) != null && !line.isEmpty()) {
      headers.add(line);
      if (startsWithIgnoreCase(line, "content-length:")) {
        contentLength = parseLength(line);
      }
    }
    byte[] body = contentLength > 0 ? readFully(in, contentLength) : new byte[0];
    return new InterceptedRequest(host, method, target, version, headers, body);
  }

  /** Writes a request upstream, dropping the framing headers and re-stating Content-Length. */
  static void writeRequest(OutputStream out, InterceptedRequest request, byte[] body)
      throws IOException {
    StringBuilder head = new StringBuilder();
    head.append(request.method())
        .append(' ')
        .append(request.target())
        .append(' ')
        .append(request.version())
        .append("\r\n");
    for (String header : request.headers()) {
      if (startsWithIgnoreCase(header, "content-length:")
          || startsWithIgnoreCase(header, "transfer-encoding:")) {
        continue;
      }
      head.append(header).append("\r\n");
    }
    head.append("Content-Length: ").append(body.length).append("\r\n\r\n");
    out.write(head.toString().getBytes(StandardCharsets.US_ASCII));
    out.write(body);
    out.flush();
  }

  /**
   * Relays one response from upstream to the client, byte-for-byte, framed by its own headers.
   * Returns true if the connection can be reused for another request, false if it must be closed.
   */
  static boolean relayResponse(InputStream upstream, OutputStream client) throws IOException {
    String statusLine = readLine(upstream);
    if (statusLine == null) {
      return false;
    }
    List<String> headers = new ArrayList<>();
    long contentLength = -1;
    boolean chunked = false;
    boolean closeRequested = false;
    String line;
    while ((line = readLine(upstream)) != null && !line.isEmpty()) {
      headers.add(line);
      String lower = line.toLowerCase(Locale.ROOT);
      if (lower.startsWith("content-length:")) {
        contentLength = parseLength(line);
      } else if (lower.startsWith("transfer-encoding:") && lower.contains("chunked")) {
        chunked = true;
      } else if (lower.startsWith("connection:") && lower.contains("close")) {
        closeRequested = true;
      }
    }

    StringBuilder head = new StringBuilder();
    head.append(statusLine).append("\r\n");
    for (String header : headers) {
      head.append(header).append("\r\n");
    }
    head.append("\r\n");
    client.write(head.toString().getBytes(StandardCharsets.US_ASCII));
    client.flush();

    if (isBodiless(statusLine)) {
      return !closeRequested;
    }
    if (chunked) {
      relayChunked(upstream, client);
      return !closeRequested;
    }
    if (contentLength >= 0) {
      relayFixed(upstream, client, contentLength);
      return !closeRequested;
    }
    // No framing at all: the response runs until the upstream closes, so this connection is done.
    relayUntilClose(upstream, client);
    return false;
  }

  /** Writes a small 403 back through the (decrypted) client connection for a blocked request. */
  static void writeBlocked(OutputStream client, String reason) throws IOException {
    String safeReason = reason == null ? "request blocked" : reason.replace('"', '\'');
    String body =
        "{\"error\":{\"message\":\"Blocked by GateWise egress policy: "
            + safeReason
            + "\",\"type\":\"forbidden\",\"code\":\"egress_policy_block\"}}";
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    String head =
        "HTTP/1.1 403 Forbidden\r\n"
            + "Content-Type: application/json\r\n"
            + "Content-Length: "
            + bytes.length
            + "\r\n"
            + "Connection: close\r\n\r\n";
    client.write(head.getBytes(StandardCharsets.US_ASCII));
    client.write(bytes);
    client.flush();
  }

  private static void relayChunked(InputStream upstream, OutputStream client) throws IOException {
    while (true) {
      String sizeLine = readLine(upstream);
      if (sizeLine == null) {
        return;
      }
      client.write((sizeLine + "\r\n").getBytes(StandardCharsets.US_ASCII));
      client.flush();
      int size;
      try {
        size = Integer.parseInt(sizeLine.split(";", 2)[0].trim(), 16);
      } catch (NumberFormatException e) {
        return;
      }
      if (size == 0) {
        // The terminating chunk; copy any trailers through to (and including) the blank line.
        String trailer;
        while ((trailer = readLine(upstream)) != null) {
          client.write((trailer + "\r\n").getBytes(StandardCharsets.US_ASCII));
          client.flush();
          if (trailer.isEmpty()) {
            break;
          }
        }
        return;
      }
      client.write(readFully(upstream, size));
      readLine(upstream); // consume the chunk's trailing CRLF
      client.write("\r\n".getBytes(StandardCharsets.US_ASCII));
      client.flush();
    }
  }

  private static void relayFixed(InputStream upstream, OutputStream client, long length)
      throws IOException {
    byte[] buffer = new byte[BUFFER_SIZE];
    long remaining = length;
    while (remaining > 0) {
      int toRead = (int) Math.min(buffer.length, remaining);
      int read = upstream.read(buffer, 0, toRead);
      if (read == -1) {
        break;
      }
      client.write(buffer, 0, read);
      client.flush();
      remaining -= read;
    }
  }

  private static void relayUntilClose(InputStream upstream, OutputStream client)
      throws IOException {
    byte[] buffer = new byte[BUFFER_SIZE];
    int read;
    while ((read = upstream.read(buffer)) != -1) {
      client.write(buffer, 0, read);
      client.flush();
    }
  }

  // A 1xx, 204 or 304 response carries no body regardless of its headers.
  private static boolean isBodiless(String statusLine) {
    String[] parts = statusLine.split(" ", 3);
    if (parts.length < 2) {
      return false;
    }
    try {
      int code = Integer.parseInt(parts[1].trim());
      return (code >= 100 && code < 200) || code == 204 || code == 304;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  private static int parseLength(String headerLine) {
    try {
      return Integer.parseInt(headerLine.substring(headerLine.indexOf(':') + 1).trim());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static boolean startsWithIgnoreCase(String value, String prefix) {
    return value.length() >= prefix.length()
        && value.regionMatches(true, 0, prefix, 0, prefix.length());
  }
}

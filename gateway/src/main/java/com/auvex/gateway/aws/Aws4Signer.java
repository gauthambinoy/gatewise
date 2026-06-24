package com.auvex.gateway.aws;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * AWS Signature Version 4 signing (the "Authorization header" variant).
 *
 * <p>This is a small, dependency-free implementation so the gateway can talk to AWS services (e.g.
 * Amazon Bedrock) without pulling in the full AWS SDK. The core {@link #authorizationHeader} method
 * is a pure function of its inputs, validated against AWS's published {@code get-vanilla} test
 * vector; {@link #signedHeaders} is the convenience layer that builds the standard host /
 * x-amz-date / x-amz-content-sha256 header set and returns everything to attach to the request.
 */
public final class Aws4Signer {

  private static final String ALGORITHM = "AWS4-HMAC-SHA256";
  private static final String HMAC = "HmacSHA256";
  private static final DateTimeFormatter AMZ_DATE =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
  private static final DateTimeFormatter DATE_STAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

  private Aws4Signer() {}

  /**
   * Signs a request and returns the headers to add to it: {@code X-Amz-Date}, {@code
   * X-Amz-Content-Sha256}, the {@code Authorization} header, and {@code X-Amz-Security-Token} when
   * the credentials are temporary. The returned path/headers must be sent exactly as signed.
   */
  public static Map<String, String> signedHeaders(
      String method,
      URI uri,
      byte[] payload,
      String region,
      String service,
      AwsCredentials credentials,
      Instant when) {

    String amzDate = AMZ_DATE.format(when);
    String dateStamp = DATE_STAMP.format(when);
    String payloadHash = hex(sha256(payload));

    // The headers that are part of the signature. Host and the x-amz-* values are always signed.
    TreeMap<String, String> toSign = new TreeMap<>();
    toSign.put("host", uri.getHost());
    toSign.put("x-amz-content-sha256", payloadHash);
    toSign.put("x-amz-date", amzDate);
    if (credentials.hasSessionToken()) {
      toSign.put("x-amz-security-token", credentials.sessionToken());
    }

    String authorization =
        authorizationHeader(
            method,
            canonicalUri(uri),
            uri.getRawQuery() == null ? "" : uri.getRawQuery(),
            toSign,
            payloadHash,
            region,
            service,
            amzDate,
            dateStamp,
            credentials.accessKeyId(),
            credentials.secretAccessKey());

    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("X-Amz-Date", amzDate);
    headers.put("X-Amz-Content-Sha256", payloadHash);
    if (credentials.hasSessionToken()) {
      headers.put("X-Amz-Security-Token", credentials.sessionToken());
    }
    headers.put("Authorization", authorization);
    return headers;
  }

  /**
   * The pure SigV4 core: builds the canonical request, the string-to-sign, derives the signing key
   * and returns the full {@code Authorization} header value. {@code signedHeaders} must be a sorted
   * map of lowercase header name to trimmed value; its key set becomes {@code SignedHeaders}.
   */
  public static String authorizationHeader(
      String method,
      String canonicalUri,
      String canonicalQuery,
      TreeMap<String, String> signedHeaders,
      String payloadHash,
      String region,
      String service,
      String amzDate,
      String dateStamp,
      String accessKey,
      String secretKey) {

    StringBuilder canonicalHeaders = new StringBuilder();
    signedHeaders.forEach((k, v) -> canonicalHeaders.append(k).append(':').append(v).append('\n'));
    String signedHeaderNames = String.join(";", signedHeaders.keySet());

    String canonicalRequest =
        String.join(
            "\n",
            method,
            canonicalUri,
            canonicalQuery,
            canonicalHeaders.toString(),
            signedHeaderNames,
            payloadHash);

    String scope = dateStamp + "/" + region + "/" + service + "/aws4_request";
    String stringToSign =
        String.join("\n", ALGORITHM, amzDate, scope, hex(sha256(canonicalRequest.getBytes(StandardCharsets.UTF_8))));

    byte[] signingKey = signingKey(secretKey, dateStamp, region, service);
    String signature = hex(hmac(signingKey, stringToSign));

    return ALGORITHM
        + " Credential="
        + accessKey
        + "/"
        + scope
        + ", SignedHeaders="
        + signedHeaderNames
        + ", Signature="
        + signature;
  }

  // The canonical URI must equal the path actually sent on the wire, so we use the raw path
  // verbatim. Callers are responsible for pre-encoding any reserved characters (e.g. the ':' in a
  // Bedrock model id) with uriEncode, and for building the request URI from that same encoded path.
  private static String canonicalUri(URI uri) {
    String path = uri.getRawPath();
    return (path == null || path.isEmpty()) ? "/" : path;
  }

  /** AWS UriEncode: A-Z a-z 0-9 - _ . ~ pass through; everything else becomes %XX (uppercase hex). */
  public static String uriEncode(String segment) {
    StringBuilder out = new StringBuilder();
    for (byte b : segment.getBytes(StandardCharsets.UTF_8)) {
      char c = (char) (b & 0xFF);
      if ((c >= 'A' && c <= 'Z')
          || (c >= 'a' && c <= 'z')
          || (c >= '0' && c <= '9')
          || c == '-'
          || c == '_'
          || c == '.'
          || c == '~') {
        out.append(c);
      } else {
        out.append('%').append(String.format("%02X", b & 0xFF));
      }
    }
    return out.toString();
  }

  private static byte[] signingKey(String secretKey, String dateStamp, String region, String service) {
    byte[] kSecret = ("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8);
    byte[] kDate = hmac(kSecret, dateStamp);
    byte[] kRegion = hmac(kDate, region);
    byte[] kService = hmac(kRegion, service);
    return hmac(kService, "aws4_request");
  }

  private static byte[] hmac(byte[] key, String data) {
    try {
      Mac mac = Mac.getInstance(HMAC);
      mac.init(new SecretKeySpec(key, HMAC));
      return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
      throw new IllegalStateException("HmacSHA256 is required for SigV4", e);
    }
  }

  private static byte[] sha256(byte[] data) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(data);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required for SigV4", e);
    }
  }

  private static String hex(byte[] bytes) {
    return HexFormat.of().formatHex(bytes);
  }
}

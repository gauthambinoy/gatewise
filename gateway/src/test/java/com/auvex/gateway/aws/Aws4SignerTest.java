package com.auvex.gateway.aws;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Validates the SigV4 core against AWS's published {@code get-vanilla} test vector — the canonical
 * fixture used to prove a Signature Version 4 implementation is correct.
 */
class Aws4SignerTest {

  // The AWS aws4_testsuite example credentials and clock.
  private static final String ACCESS_KEY = "AKIDEXAMPLE";
  private static final String SECRET_KEY = "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY";
  private static final String EMPTY_SHA256 =
      "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

  @Test
  void matchesAwsGetVanillaVector() {
    TreeMap<String, String> signed = new TreeMap<>();
    signed.put("host", "example.amazonaws.com");
    signed.put("x-amz-date", "20150830T123600Z");

    String authorization =
        Aws4Signer.authorizationHeader(
            "GET",
            "/",
            "",
            signed,
            EMPTY_SHA256,
            "us-east-1",
            "service",
            "20150830T123600Z",
            "20150830",
            ACCESS_KEY,
            SECRET_KEY);

    assertThat(authorization)
        .isEqualTo(
            "AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20150830/us-east-1/service/aws4_request, "
                + "SignedHeaders=host;x-amz-date, "
                + "Signature=5fa00fa31553b73ebf1942676e86291e8372ff2a2260956d9b8aae1d763fbf31");
  }

  @Test
  void signedHeadersBuildsTheStandardHeaderSet() {
    Map<String, String> headers =
        Aws4Signer.signedHeaders(
            "POST",
            URI.create("https://bedrock-runtime.eu-west-1.amazonaws.com/model/x/invoke"),
            "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8),
            "eu-west-1",
            "bedrock",
            new AwsCredentials(ACCESS_KEY, SECRET_KEY, null),
            Instant.parse("2026-06-24T10:00:00Z"));

    assertThat(headers).containsKeys("Authorization", "X-Amz-Date", "X-Amz-Content-Sha256");
    assertThat(headers).doesNotContainKey("X-Amz-Security-Token");
    assertThat(headers.get("Authorization"))
        .startsWith("AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20260624/eu-west-1/bedrock/aws4_request")
        .contains("SignedHeaders=host;x-amz-content-sha256;x-amz-date");
    assertThat(headers.get("X-Amz-Date")).isEqualTo("20260624T100000Z");
  }

  @Test
  void temporaryCredentialsAddAndSignTheSecurityToken() {
    Map<String, String> headers =
        Aws4Signer.signedHeaders(
            "POST",
            URI.create("https://bedrock-runtime.us-east-1.amazonaws.com/model/x/invoke"),
            new byte[0],
            "us-east-1",
            "bedrock",
            new AwsCredentials(ACCESS_KEY, SECRET_KEY, "SESSIONTOKEN123"),
            Instant.parse("2026-06-24T10:00:00Z"));

    assertThat(headers).containsEntry("X-Amz-Security-Token", "SESSIONTOKEN123");
    assertThat(headers.get("Authorization")).contains("x-amz-security-token");
  }
}

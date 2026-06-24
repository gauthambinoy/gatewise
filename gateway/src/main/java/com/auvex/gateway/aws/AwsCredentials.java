package com.auvex.gateway.aws;

/**
 * AWS credentials used to sign a request. {@code sessionToken} is optional and only set for
 * temporary (STS) credentials; when present it is signed and sent as {@code X-Amz-Security-Token}.
 */
public record AwsCredentials(String accessKeyId, String secretAccessKey, String sessionToken) {

  /** True when both the access key id and secret are present — i.e. signing can proceed. */
  public boolean isComplete() {
    return accessKeyId != null
        && !accessKeyId.isBlank()
        && secretAccessKey != null
        && !secretAccessKey.isBlank();
  }

  /** True when temporary credentials carry a session token to sign. */
  public boolean hasSessionToken() {
    return sessionToken != null && !sessionToken.isBlank();
  }
}

package com.auvex.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Amazon Bedrock provider settings. When enabled, requests whose resolved model starts with {@code
 * bedrock/} are signed with AWS SigV4 and sent to the Bedrock Runtime {@code InvokeModel} API. The
 * model id after the prefix is the Bedrock model id, e.g. {@code
 * bedrock/anthropic.claude-3-5-sonnet-20240620-v1:0}.
 *
 * <p>Claude-family Bedrock models speak the Anthropic Messages body, so the gateway reuses the
 * Anthropic translation and just adapts it for Bedrock (drops the top-level {@code model}, adds
 * {@code anthropic_version}). Off by default; supply an AWS region + credentials to switch it on.
 *
 * @param enabled route {@code bedrock/*} models to Amazon Bedrock
 * @param region the AWS region hosting Bedrock (e.g. {@code us-east-1})
 * @param accessKeyId AWS access key id
 * @param secretAccessKey AWS secret access key
 * @param sessionToken optional STS session token for temporary credentials
 * @param anthropicVersion the {@code anthropic_version} Bedrock expects in the body
 * @param maxTokens default {@code max_tokens} when the caller omits it
 */
@ConfigurationProperties(prefix = "auvex.bedrock")
public record BedrockProperties(
    boolean enabled,
    String region,
    String accessKeyId,
    String secretAccessKey,
    String sessionToken,
    String anthropicVersion,
    Integer maxTokens) {

  public BedrockProperties {
    if (region == null || region.isBlank()) {
      region = "us-east-1";
    }
    if (anthropicVersion == null || anthropicVersion.isBlank()) {
      anthropicVersion = "bedrock-2023-05-31";
    }
    if (maxTokens == null || maxTokens <= 0) {
      maxTokens = 1024;
    }
  }

  /** The Bedrock Runtime host for the configured region. */
  public String host() {
    return "bedrock-runtime." + region + ".amazonaws.com";
  }
}

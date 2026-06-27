package com.auvex.gateway.multimodal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How the gateway treats image parts in a multimodal request.
 *
 * @param policy ALLOW (default — forward, but audit that an image was present), STRIP (drop the
 *     images and keep the text), or BLOCK (reject any request that carries an image)
 */
@ConfigurationProperties(prefix = "auvex.multimodal")
public record MultimodalProperties(Policy policy) {

  public MultimodalProperties {
    if (policy == null) {
      policy = Policy.ALLOW;
    }
  }

  /** What to do with image content in a prompt. */
  public enum Policy {
    ALLOW,
    STRIP,
    BLOCK
  }
}

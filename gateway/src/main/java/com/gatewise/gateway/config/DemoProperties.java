package com.gatewise.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Optional demo seed: a sandbox tenant + API key + sample data, so the console's "Try the live
 * demo" button works with no setup. Off by default; never enable it in a real deployment.
 *
 * @param enabled whether to seed the demo data on startup
 * @param key the sandbox API key the demo button signs in with
 * @param tenantName the display name for the demo tenant
 */
@ConfigurationProperties(prefix = "gatewise.demo")
public record DemoProperties(boolean enabled, String key, String tenantName) {

  public DemoProperties {
    if (key == null || key.isBlank()) {
      key = "gatewise_demo_key";
    }
    if (tenantName == null || tenantName.isBlank()) {
      tenantName = "Demo Org";
    }
  }
}

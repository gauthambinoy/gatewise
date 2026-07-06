package com.gatewise.gateway.config;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The egress / forward transparent-proxy (TLS MITM) that catches AI traffic from apps that don't
 * cooperatively point at the gateway.
 *
 * <p>Off by default: when {@code enabled} is false no listener is opened and the main 8080 app is
 * untouched. When on, a separate proxy listener runs on {@code port}; a CONNECT to a host in {@code
 * interceptHosts} is TLS-intercepted and run through the governance pipeline, while every other
 * host is tunnelled opaquely. {@code blockUncovered} is the 8.3 mandatory-routing switch: with it
 * on, an intercepted AI request that fails policy is blocked rather than forwarded.
 *
 * @param enabled start the egress proxy listener (default false)
 * @param port the port the proxy listens on (default 8888); 0 binds an ephemeral port (tests)
 * @param interceptHosts the AI hosts to TLS-intercept; anything else is tunnelled opaquely
 * @param blockUncovered when true, an intercepted request that fails policy is blocked, not
 *     forwarded (default false — detect-and-forward, so it's observed before it's enforced)
 * @param tenantId the tenant intercepted traffic is attributed to for policy + audit; must be an
 *     existing tenant when enabled (a placeholder all-zero UUID is used when unset)
 * @param caFile if set, the root CA certificate is also written here (PEM) on startup, for the OS /
 *     client trust-store install step
 */
@ConfigurationProperties(prefix = "gatewise.egress")
public record EgressProperties(
    boolean enabled,
    Integer port,
    Set<String> interceptHosts,
    boolean blockUncovered,
    String tenantId,
    String caFile) {

  private static final int DEFAULT_PORT = 8888;
  private static final Set<String> DEFAULT_HOSTS =
      Set.of("api.openai.com", "api.anthropic.com", "generativelanguage.googleapis.com");

  public EgressProperties {
    if (port == null) {
      port = DEFAULT_PORT;
    }
    interceptHosts =
        (interceptHosts == null || interceptHosts.isEmpty())
            ? DEFAULT_HOSTS
            : interceptHosts.stream()
                .map(host -> host.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
  }

  /** Whether a CONNECT to {@code host} should be TLS-intercepted (vs tunnelled opaquely). */
  public boolean intercepts(String host) {
    return host != null && interceptHosts.contains(host.toLowerCase(Locale.ROOT));
  }

  /** The tenant intercepted traffic is attributed to (an all-zero placeholder UUID when unset). */
  public UUID resolveTenantId() {
    if (tenantId == null || tenantId.isBlank()) {
      return new UUID(0L, 0L);
    }
    return UUID.fromString(tenantId.trim());
  }
}

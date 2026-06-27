package com.auvex.gateway.egress;

import com.auvex.gateway.config.EgressProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the egress proxy's beans, and only when {@code auvex.egress.enabled=true}.
 *
 * <p>Gating the whole subsystem on one switch here keeps the default path clean: with egress off,
 * the CA is never generated and no listener bean exists, so the main app is exactly as it was. The
 * proxy server is built here (rather than component-scanned) so its upstream dialer is an explicit
 * constructor argument — the one seam an integration test overrides to reach a local mock provider.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "auvex.egress", name = "enabled", havingValue = "true")
public class EgressConfig {

  /** The in-memory root CA that signs the per-host leaf certificates the proxy presents. */
  @Bean
  public CertificateAuthority egressCertificateAuthority() {
    return new CertificateAuthority();
  }

  /** The proxy listener itself, dialling real upstreams over the JVM's default trust store. */
  @Bean
  public EgressProxyServer egressProxyServer(
      LeafCertificateCache certificates, EgressGovernor governor, EgressProperties properties) {
    return new EgressProxyServer(
        certificates, governor, properties, TlsUpstreamDialer.systemDefault());
  }
}

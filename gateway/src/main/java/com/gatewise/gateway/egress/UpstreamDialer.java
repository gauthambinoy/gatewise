package com.gatewise.gateway.egress;

import java.io.IOException;
import java.net.Socket;

/**
 * Opens the real connection to the genuine upstream provider when re-originating an intercepted
 * request.
 *
 * <p>Pulled out behind an interface for one reason: it's the single seam a test needs to override,
 * so an integration test can point re-origination at a local mock TLS server instead of the public
 * internet. Production uses {@link TlsUpstreamDialer}.
 */
@FunctionalInterface
public interface UpstreamDialer {

  /** Connects (and completes the TLS handshake) to the upstream {@code host:port}. */
  Socket connect(String host, int port) throws IOException;
}

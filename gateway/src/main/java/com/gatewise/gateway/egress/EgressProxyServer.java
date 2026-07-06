package com.gatewise.gateway.egress;

import com.gatewise.gateway.config.EgressProperties;
import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateEncodingException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * The egress / forward transparent-proxy: a separate listener that catches AI-bound HTTPS from apps
 * that don't cooperatively point at the gateway, and routes it through the same governance
 * pipeline.
 *
 * <p>It speaks the HTTP CONNECT tunnel protocol. For a host that isn't in the configured AI set it
 * tunnels opaquely — the proxy never sees the plaintext and just relays bytes. For a configured AI
 * host it performs TLS interception (MITM): it presents a CA-signed leaf for that host so a client
 * that trusts the {@link CertificateAuthority} root completes the handshake, terminates TLS, reads
 * the request, governs it ({@link EgressGovernor}), opens a real verified TLS connection to the
 * true provider, forwards the (redacted) request, and relays the response back. None of this is
 * reachable unless {@code gatewise.egress.enabled=true}, so the main 8080 app is untouched by
 * default.
 *
 * <p>This implementation is plain JDK sockets and {@link SSLSocket} on Java 21 virtual threads (one
 * per connection) — no Netty — because the gateway already runs request-per-virtual-thread, a
 * blocking thread-per-connection proxy is simple and scales the same way, and {@code
 * SSLSocketFactory.createSocket(Socket, …)} cleanly wraps an accepted socket for server-side MITM
 * while a fresh {@link SSLSocket} dials the upstream. A Netty pipeline would add a heavy dependency
 * for no gain here.
 *
 * <p><strong>MANDATORY ROUTING (8.3).</strong> This proxy can only govern traffic that is actually
 * sent through it. Making it un-bypassable is an environment job, not something the gateway can
 * enforce from inside the JVM: point clients at it (an OS/system proxy, a {@code .pac} file, or the
 * companion desktop app), then block direct egress to the AI hosts at the firewall / DNS so the
 * only way out is via this proxy. With {@code gatewise.egress.block-uncovered=true} an intercepted
 * request that fails policy is then refused outright rather than forwarded. See {@code
 * docs/EGRESS.md}.
 */
public class EgressProxyServer implements SmartLifecycle {

  private static final Logger LOG = LoggerFactory.getLogger(EgressProxyServer.class);
  private static final String CONNECT_ESTABLISHED = "HTTP/1.1 200 Connection Established\r\n\r\n";
  private static final int CONNECT_TIMEOUT_MS = 10_000;

  private final LeafCertificateCache certificates;
  private final EgressGovernor governor;
  private final EgressProperties properties;
  private final UpstreamDialer dialer;

  private volatile ServerSocket serverSocket;
  private volatile ExecutorService workers;
  private volatile boolean running;

  public EgressProxyServer(
      LeafCertificateCache certificates,
      EgressGovernor governor,
      EgressProperties properties,
      UpstreamDialer dialer) {
    this.certificates = certificates;
    this.governor = governor;
    this.properties = properties;
    this.dialer = dialer;
  }

  @Override
  public synchronized void start() {
    if (running) {
      return;
    }
    maybeWriteCaFile();
    try {
      ServerSocket socket = new ServerSocket();
      socket.setReuseAddress(true);
      socket.bind(new InetSocketAddress(properties.port()));
      this.serverSocket = socket;
    } catch (IOException e) {
      throw new IllegalStateException(
          "Failed to start the egress proxy on port " + properties.port(), e);
    }
    this.workers = Executors.newVirtualThreadPerTaskExecutor();
    this.running = true;
    Thread acceptor = new Thread(this::acceptLoop, "egress-proxy-acceptor");
    acceptor.setDaemon(true);
    acceptor.start();
    LOG.info(
        "Egress proxy listening on port {}, intercepting {}",
        boundPort(),
        properties.interceptHosts());
  }

  @Override
  public synchronized void stop() {
    running = false;
    closeQuietly(serverSocket);
    if (workers != null) {
      workers.shutdownNow();
    }
    LOG.info("Egress proxy stopped");
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  @Override
  public int getPhase() {
    // Start last and stop first: the network listener should only be up while the app is fully up.
    return Integer.MAX_VALUE;
  }

  /** The port the proxy actually bound to (useful when {@code port=0} selects an ephemeral one). */
  public int boundPort() {
    ServerSocket socket = serverSocket;
    return socket == null ? -1 : socket.getLocalPort();
  }

  private void acceptLoop() {
    while (running) {
      try {
        Socket client = serverSocket.accept();
        workers.execute(() -> handle(client));
      } catch (IOException e) {
        if (running) {
          LOG.warn("Egress proxy accept failed: {}", e.getMessage());
        }
        // Otherwise the socket was closed by stop(); fall through and exit the loop.
      }
    }
  }

  private void handle(Socket client) {
    try {
      client.setTcpNoDelay(true);
      InputStream clientIn = client.getInputStream();
      OutputStream clientOut = client.getOutputStream();

      String requestLine = ProxyHttp.readLine(clientIn);
      if (requestLine == null) {
        closeQuietly(client);
        return;
      }
      // Drain the rest of the CONNECT request's headers up to the blank line.
      String line;
      while ((line = ProxyHttp.readLine(clientIn)) != null && !line.isEmpty()) {
        // headers on a CONNECT carry nothing we need
      }

      String[] parts = requestLine.split(" ", 3);
      if (parts.length < 2 || !"CONNECT".equalsIgnoreCase(parts[0])) {
        // This is a forward proxy for TLS tunnels only; a plain proxied request isn't supported.
        writeStatus(clientOut, "405 Method Not Allowed", "Only CONNECT tunnelling is supported.");
        closeQuietly(client);
        return;
      }

      HostPort target = HostPort.parse(parts[1]);
      clientOut.write(CONNECT_ESTABLISHED.getBytes(StandardCharsets.US_ASCII));
      clientOut.flush();

      if (properties.intercepts(target.host())) {
        intercept(client, target);
      } else {
        tunnel(client, clientIn, clientOut, target);
      }
    } catch (IOException e) {
      LOG.debug("Egress connection error: {}", e.getMessage());
      closeQuietly(client);
    }
  }

  // TLS MITM: terminate the client's TLS with a minted leaf, govern each request, re-originate.
  private void intercept(Socket client, HostPort target) {
    SSLSocket tls;
    try {
      SSLContext serverContext = certificates.serverContextFor(target.host());
      tls =
          (SSLSocket)
              serverContext
                  .getSocketFactory()
                  .createSocket(client, target.host(), target.port(), true);
      tls.setUseClientMode(false);
      tls.startHandshake();
    } catch (IOException e) {
      // The client didn't trust our CA, or aborted the handshake — nothing more to do here.
      LOG.debug("MITM handshake with client failed for {}: {}", target.host(), e.getMessage());
      closeQuietly(client);
      return;
    }

    Socket upstream = null;
    try {
      InputStream decryptedIn = new BufferedInputStream(tls.getInputStream());
      OutputStream decryptedOut = tls.getOutputStream();
      InputStream upstreamIn = null;
      OutputStream upstreamOut = null;

      InterceptedRequest request;
      while ((request = ProxyHttp.readRequest(decryptedIn, target.host())) != null) {
        GovernanceDecision decision = governor.govern(request);
        if (!decision.allowed()) {
          ProxyHttp.writeBlocked(decryptedOut, decision.blockReason());
          break;
        }
        if (upstream == null) {
          upstream = dialer.connect(target.host(), target.port());
          upstreamIn = new BufferedInputStream(upstream.getInputStream());
          upstreamOut = upstream.getOutputStream();
        }
        ProxyHttp.writeRequest(upstreamOut, request, decision.forwardBody());
        if (!ProxyHttp.relayResponse(upstreamIn, decryptedOut)) {
          break;
        }
      }
    } catch (IOException e) {
      LOG.debug("MITM relay for {} ended: {}", target.host(), e.getMessage());
    } finally {
      closeQuietly(upstream);
      closeQuietly(tls);
    }
  }

  // Opaque pass-through: relay bytes both ways without ever decrypting them.
  private void tunnel(
      Socket client, InputStream clientIn, OutputStream clientOut, HostPort target) {
    Socket upstream = new Socket();
    try {
      upstream.connect(new InetSocketAddress(target.host(), target.port()), CONNECT_TIMEOUT_MS);
    } catch (IOException e) {
      LOG.debug("Opaque tunnel to {}:{} failed: {}", target.host(), target.port(), e.getMessage());
      closeQuietly(upstream);
      closeQuietly(client);
      return;
    }

    Socket up = upstream;
    InputStream upstreamIn;
    OutputStream upstreamOut;
    try {
      upstreamIn = up.getInputStream();
      upstreamOut = up.getOutputStream();
    } catch (IOException e) {
      closeQuietly(up);
      closeQuietly(client);
      return;
    }

    // Pump client->upstream on a worker thread; pump upstream->client on this one. When either
    // direction ends, both sockets are closed, which unblocks the other pump.
    workers.execute(
        () -> {
          pumpQuietly(clientIn, upstreamOut);
          closeQuietly(up);
          closeQuietly(client);
        });
    pumpQuietly(upstreamIn, clientOut);
    closeQuietly(up);
    closeQuietly(client);
  }

  // 8.4 cert lifecycle: drop the root CA to disk on startup so an OS/client trust store can load
  // it.
  private void maybeWriteCaFile() {
    String path = properties.caFile();
    if (path == null || path.isBlank()) {
      return;
    }
    try {
      Files.writeString(Path.of(path.trim()), Pem.encode(certificates.rootCertificate()));
      LOG.info("Wrote egress CA certificate to {}", path);
    } catch (IOException | CertificateEncodingException e) {
      LOG.warn("Failed to write the egress CA certificate to {}: {}", path, e.getMessage());
    }
  }

  private static void pumpQuietly(InputStream in, OutputStream out) {
    byte[] buffer = new byte[8192];
    try {
      int read;
      while ((read = in.read(buffer)) != -1) {
        out.write(buffer, 0, read);
        out.flush();
      }
    } catch (IOException e) {
      // Expected when either side hangs up or the tunnel is torn down.
      LOG.trace("Tunnel pump ended: {}", e.getMessage());
    }
  }

  private static void writeStatus(OutputStream out, String status, String message)
      throws IOException {
    byte[] body = message.getBytes(StandardCharsets.UTF_8);
    String head =
        "HTTP/1.1 "
            + status
            + "\r\nContent-Type: text/plain\r\nContent-Length: "
            + body.length
            + "\r\nConnection: close\r\n\r\n";
    out.write(head.getBytes(StandardCharsets.US_ASCII));
    out.write(body);
    out.flush();
  }

  private static void closeQuietly(Closeable closeable) {
    if (closeable == null) {
      return;
    }
    try {
      closeable.close();
    } catch (IOException e) {
      LOG.trace("Close failed: {}", e.getMessage());
    }
  }

  /** A {@code host:port} pair parsed from a CONNECT target, defaulting to 443. */
  record HostPort(String host, int port) {
    static HostPort parse(String authority) {
      int colon = authority.lastIndexOf(':');
      if (colon < 0) {
        return new HostPort(authority, 443);
      }
      int port;
      try {
        port = Integer.parseInt(authority.substring(colon + 1).trim());
      } catch (NumberFormatException e) {
        port = 443;
      }
      return new HostPort(authority.substring(0, colon), port);
    }
  }
}

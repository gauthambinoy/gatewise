package com.gatewise.gateway.egress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatewise.gateway.audit.AuditEntry;
import com.gatewise.gateway.audit.AuditSink;
import com.gatewise.gateway.audit.Verdict;
import com.gatewise.gateway.config.EgressProperties;
import com.gatewise.gateway.config.InjectionProperties;
import com.gatewise.gateway.injection.InjectionScanner;
import com.gatewise.gateway.injection.InstructionOverrideRule;
import com.gatewise.gateway.policy.Decision;
import com.gatewise.gateway.policy.PolicyEnforcement;
import com.gatewise.gateway.redaction.EmailDetector;
import com.gatewise.gateway.redaction.PromptRedactor;
import com.gatewise.gateway.redaction.RedactionEngine;
import com.gatewise.gateway.redaction.TokenMasker;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stands the proxy up on an ephemeral port and drives it over real sockets.
 *
 * <p>Covers both branches: an opaque CONNECT to a non-intercepted host (bytes relayed untouched,
 * nothing governed) and a full TLS MITM of an intercepted host against a local mock provider — a
 * client that trusts the proxy CA, a redacted request actually delivered upstream, and an audit
 * record written. The upstream-trust seam ({@link UpstreamDialer}) is what lets the re-origination
 * leg reach the mock instead of the real internet.
 */
class EgressProxyIntegrationTest {

  private static final Logger LOG = LoggerFactory.getLogger(EgressProxyIntegrationTest.class);
  private static final int TIMEOUT_MS = 15_000;
  private static final String AI_HOST = "api.openai.com";

  private final ExecutorService executor = Executors.newCachedThreadPool();
  private final List<AutoCloseable> resources = new ArrayList<>();
  private EgressProxyServer server;

  @AfterEach
  void cleanup() {
    if (server != null) {
      server.stop();
    }
    for (AutoCloseable resource : resources) {
      quietClose(resource);
    }
    executor.shutdownNow();
  }

  @Test
  void opaquelyTunnelsANonInterceptedHost() throws Exception {
    // A plain echo server stands in for an opaque (non-AI) destination.
    ServerSocket echo = new ServerSocket(0);
    resources.add(echo);
    int echoPort = echo.getLocalPort();
    executor.execute(() -> runEcho(echo));

    RecordingAuditSink audit = new RecordingAuditSink();
    UpstreamDialer mustNotDial =
        (host, port) -> {
          throw new IOException("the dialer must not be used for an opaque host");
        };
    int proxyPort = startProxy(mustNotDial, audit, new CertificateAuthority());

    try (Socket raw = new Socket("localhost", proxyPort)) {
      raw.setSoTimeout(TIMEOUT_MS);
      OutputStream out = raw.getOutputStream();
      InputStream in = raw.getInputStream();
      out.write(
          ("CONNECT localhost:" + echoPort + " HTTP/1.1\r\n\r\n")
              .getBytes(StandardCharsets.US_ASCII));
      out.flush();
      assertThat(ProxyHttp.readLine(in)).contains("200");
      drainHeaders(in);

      byte[] payload = "ping-through-the-tunnel".getBytes(StandardCharsets.UTF_8);
      out.write(payload);
      out.flush();
      byte[] echoed = ProxyHttp.readFully(in, payload.length);
      assertThat(new String(echoed, StandardCharsets.UTF_8)).isEqualTo("ping-through-the-tunnel");
    }

    // Opaque traffic is never decrypted, so there is nothing to govern or audit.
    assertThat(audit.entries()).isEmpty();
  }

  @Test
  void mitMsAnInterceptedHostThenRedactsAndAuditsTheForwardedRequest() throws Exception {
    // A mock TLS upstream presenting a cert for the AI host, with its own CA.
    CertificateAuthority upstreamCa = new CertificateAuthority();
    SSLContext upstreamServerContext =
        new LeafCertificateCache(upstreamCa).serverContextFor(AI_HOST);
    SSLServerSocket upstream =
        (SSLServerSocket) upstreamServerContext.getServerSocketFactory().createServerSocket(0);
    resources.add(upstream);
    int upstreamPort = upstream.getLocalPort();
    Future<byte[]> forwardedBody = executor.submit(() -> handleUpstream(upstream));

    // Re-origination is pointed at the local mock, trusting the mock's CA.
    SSLSocketFactory upstreamClient =
        clientTrusting(upstreamCa.rootCertificate()).getSocketFactory();
    UpstreamDialer dialer =
        (host, port) -> {
          SSLSocket socket = (SSLSocket) upstreamClient.createSocket("localhost", upstreamPort);
          socket.startHandshake();
          return socket;
        };

    RecordingAuditSink audit = new RecordingAuditSink();
    CertificateAuthority proxyCa = new CertificateAuthority();
    int proxyPort = startProxy(dialer, audit, proxyCa);

    // The client trusts the proxy's CA, so the MITM handshake completes — that's the interception.
    SSLSocketFactory clientFactory = clientTrusting(proxyCa.rootCertificate()).getSocketFactory();
    String body =
        "{\"model\":\"gpt-4o\",\"messages\":[{\"role\":\"user\","
            + "\"content\":\"reach me at alice@example.com\"}]}";
    String response = sendThroughMitm(proxyPort, clientFactory, body);

    assertThat(response).contains("chatcmpl-egress-test");

    // What actually reached the upstream had the email redacted out.
    String forwarded =
        new String(forwardedBody.get(TIMEOUT_MS, TimeUnit.MILLISECONDS), StandardCharsets.UTF_8);
    assertThat(forwarded).doesNotContain("alice@example.com");
    assertThat(forwarded).contains("EMAIL_REDACTED");

    // And the intercepted call was audited, as redacted, with the raw value never recorded.
    assertThat(audit.entries())
        .anySatisfy(
            entry -> {
              assertThat(entry.verdict()).isEqualTo(Verdict.REDACTED);
              assertThat(entry.promptRedacted()).doesNotContain("alice@example.com");
            });
  }

  private int startProxy(
      UpstreamDialer dialer, RecordingAuditSink audit, CertificateAuthority proxyCa) {
    LeafCertificateCache certificates = new LeafCertificateCache(proxyCa);
    PolicyEnforcement policy = mock(PolicyEnforcement.class);
    when(policy.evaluate(any())).thenReturn(new Decision(true, List.of(), "allowed"));
    PromptRedactor redactor =
        new PromptRedactor(new RedactionEngine(List.of(new EmailDetector()), new TokenMasker()));
    InjectionScanner scanner = new InjectionScanner(List.of(new InstructionOverrideRule()));
    EgressProperties properties = new EgressProperties(true, 0, Set.of(AI_HOST), false, null, null);
    EgressGovernor governor =
        new EgressGovernor(
            redactor,
            scanner,
            new InjectionProperties(true, false),
            policy,
            audit,
            properties,
            new ObjectMapper());
    server = new EgressProxyServer(certificates, governor, properties, dialer);
    server.start();
    return server.boundPort();
  }

  // Drives one request through the MITM tunnel and returns the response body the client receives.
  private String sendThroughMitm(int proxyPort, SSLSocketFactory clientFactory, String jsonBody)
      throws IOException {
    try (Socket raw = new Socket("localhost", proxyPort)) {
      raw.setSoTimeout(TIMEOUT_MS);
      OutputStream rawOut = raw.getOutputStream();
      InputStream rawIn = raw.getInputStream();
      rawOut.write(
          ("CONNECT " + AI_HOST + ":443 HTTP/1.1\r\nHost: " + AI_HOST + ":443\r\n\r\n")
              .getBytes(StandardCharsets.US_ASCII));
      rawOut.flush();
      assertThat(ProxyHttp.readLine(rawIn)).contains("200");
      drainHeaders(rawIn);

      // autoClose=false: the outer try-with-resources owns the raw socket.
      SSLSocket tls = (SSLSocket) clientFactory.createSocket(raw, AI_HOST, 443, false);
      tls.setUseClientMode(true);
      tls.startHandshake();
      OutputStream tlsOut = tls.getOutputStream();
      InputStream tlsIn = new BufferedInputStream(tls.getInputStream());

      byte[] body = jsonBody.getBytes(StandardCharsets.UTF_8);
      String head =
          "POST /v1/chat/completions HTTP/1.1\r\nHost: "
              + AI_HOST
              + "\r\nContent-Type: application/json\r\nContent-Length: "
              + body.length
              + "\r\n\r\n";
      tlsOut.write(head.getBytes(StandardCharsets.US_ASCII));
      tlsOut.write(body);
      tlsOut.flush();

      assertThat(ProxyHttp.readLine(tlsIn)).contains("200");
      int contentLength = 0;
      String header;
      while ((header = ProxyHttp.readLine(tlsIn)) != null && !header.isEmpty()) {
        if (header.toLowerCase(Locale.ROOT).startsWith("content-length:")) {
          contentLength = Integer.parseInt(header.substring(header.indexOf(':') + 1).trim());
        }
      }
      byte[] responseBody = ProxyHttp.readFully(tlsIn, contentLength);
      tls.close();
      return new String(responseBody, StandardCharsets.UTF_8);
    }
  }

  // Mock upstream: read the (forwarded) request, answer 200, and hold the socket open until the
  // proxy tears the connection down so the response is relayed in full before we close.
  private static byte[] handleUpstream(SSLServerSocket server) throws IOException {
    try (SSLSocket socket = (SSLSocket) server.accept()) {
      socket.setSoTimeout(TIMEOUT_MS);
      InputStream in = new BufferedInputStream(socket.getInputStream());
      InterceptedRequest request = ProxyHttp.readRequest(in, AI_HOST);
      byte[] body =
          "{\"id\":\"chatcmpl-egress-test\",\"choices\":[]}".getBytes(StandardCharsets.UTF_8);
      OutputStream out = socket.getOutputStream();
      String head =
          "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "
              + body.length
              + "\r\n\r\n";
      out.write(head.getBytes(StandardCharsets.US_ASCII));
      out.write(body);
      out.flush();
      byte[] captured = request == null ? new byte[0] : request.body();
      while (in.read() != -1) {
        // wait for the proxy to close this connection before returning
      }
      return captured;
    }
  }

  private void runEcho(ServerSocket server) {
    try (Socket socket = server.accept()) {
      socket.setSoTimeout(TIMEOUT_MS);
      InputStream in = socket.getInputStream();
      OutputStream out = socket.getOutputStream();
      byte[] buffer = new byte[1024];
      int read;
      while ((read = in.read(buffer)) != -1) {
        out.write(buffer, 0, read);
        out.flush();
      }
    } catch (IOException e) {
      LOG.debug("Echo server stopped: {}", e.getMessage());
    }
  }

  private static void drainHeaders(InputStream in) throws IOException {
    String line;
    while ((line = ProxyHttp.readLine(in)) != null && !line.isEmpty()) {
      // consume up to the blank line
    }
  }

  private static SSLContext clientTrusting(X509Certificate root) throws Exception {
    KeyStore trust = KeyStore.getInstance("PKCS12");
    trust.load(null, null);
    trust.setCertificateEntry("root", root);
    TrustManagerFactory trustManagers =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trustManagers.init(trust);
    SSLContext context = SSLContext.getInstance("TLS");
    context.init(null, trustManagers.getTrustManagers(), null);
    return context;
  }

  private static void quietClose(AutoCloseable closeable) {
    try {
      closeable.close();
    } catch (Exception e) {
      LOG.debug("Cleanup close failed: {}", e.getMessage());
    }
  }

  /** An in-memory audit sink that just records what it's given, for assertions. */
  private static final class RecordingAuditSink implements AuditSink {
    private final List<AuditEntry> entries = new CopyOnWriteArrayList<>();

    @Override
    public void record(AuditEntry entry) {
      entries.add(entry);
    }

    List<AuditEntry> entries() {
      return entries;
    }
  }
}

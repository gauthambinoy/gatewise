package com.auvex.gateway.egress;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Dials the genuine upstream provider over TLS, using the JVM's default trust store.
 *
 * <p>This is the second TLS leg of the MITM: after the proxy has terminated the client's connection
 * and governed the request, it opens a real, verified TLS connection to the true provider. Hostname
 * verification is left on ({@code HTTPS} endpoint identification) and SNI is set, so a tampered or
 * mis-issued upstream certificate is still rejected — the proxy impersonates the provider to the
 * client, but never lets the provider be impersonated to the proxy.
 */
public final class TlsUpstreamDialer implements UpstreamDialer {

  private static final int CONNECT_TIMEOUT_MS = 10_000;

  private final SSLSocketFactory factory;

  public TlsUpstreamDialer(SSLSocketFactory factory) {
    this.factory = factory;
  }

  /** A dialer backed by the JVM's default TLS trust store (the public CAs). */
  public static TlsUpstreamDialer systemDefault() {
    return new TlsUpstreamDialer((SSLSocketFactory) SSLSocketFactory.getDefault());
  }

  @Override
  public Socket connect(String host, int port) throws IOException {
    SSLSocket socket = (SSLSocket) factory.createSocket();
    socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
    SSLParameters parameters = socket.getSSLParameters();
    parameters.setServerNames(List.of(new SNIHostName(host)));
    parameters.setEndpointIdentificationAlgorithm("HTTPS");
    socket.setSSLParameters(parameters);
    socket.startHandshake();
    return socket;
  }
}

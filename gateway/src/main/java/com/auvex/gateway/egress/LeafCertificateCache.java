package com.auvex.gateway.egress;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Caches one CA-signed leaf certificate (and its server-side TLS context) per intercepted host.
 *
 * <p>Minting a leaf means a fresh RSA key pair and a signature, which is far too expensive to do on
 * every connection — but the proxy only ever impersonates a small, fixed set of AI hosts, so the
 * leaf and the {@link SSLContext} built around it are computed once per host and reused. The
 * context presents the chain {@code [leaf, root]} so a client that trusts the root validates the
 * leaf.
 */
@Component
@ConditionalOnProperty(prefix = "auvex.egress", name = "enabled", havingValue = "true")
public class LeafCertificateCache {

  private final CertificateAuthority ca;
  private final Map<String, IssuedCertificate> leaves = new ConcurrentHashMap<>();
  private final Map<String, SSLContext> contexts = new ConcurrentHashMap<>();

  public LeafCertificateCache(CertificateAuthority ca) {
    this.ca = ca;
  }

  /** The root CA certificate clients must trust (and that signs every leaf). */
  public java.security.cert.X509Certificate rootCertificate() {
    return ca.rootCertificate();
  }

  /** The leaf certificate for {@code host}, minted once and cached. */
  public IssuedCertificate leafFor(String host) {
    return leaves.computeIfAbsent(host, ca::issueFor);
  }

  /** A server-mode TLS context for {@code host}, built once and cached, that serves its leaf. */
  public SSLContext serverContextFor(String host) {
    return contexts.computeIfAbsent(host, this::buildServerContext);
  }

  private SSLContext buildServerContext(String host) {
    try {
      IssuedCertificate leaf = leafFor(host);
      KeyStore keyStore = KeyStore.getInstance("PKCS12");
      keyStore.load(null, null);
      char[] noPassword = new char[0];
      keyStore.setKeyEntry(
          "leaf",
          leaf.privateKey(),
          noPassword,
          new Certificate[] {leaf.certificate(), ca.rootCertificate()});

      KeyManagerFactory keyManagers =
          KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      keyManagers.init(keyStore, noPassword);

      SSLContext context = SSLContext.getInstance("TLS");
      context.init(keyManagers.getKeyManagers(), null, null);
      return context;
    } catch (GeneralSecurityException | IOException e) {
      throw new IllegalStateException("Failed to build the TLS context for " + host, e);
    }
  }
}

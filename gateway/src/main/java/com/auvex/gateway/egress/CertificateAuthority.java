package com.auvex.gateway.egress;

import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * The certificate authority behind the egress proxy's TLS interception.
 *
 * <p>A root CA — installed once and trusted on a client machine — signs a short-lived leaf
 * certificate per intercepted host on the fly, so the proxy can terminate the TLS connection to an
 * AI provider, run the request through the governance pipeline, and re-originate it. This is the
 * foundation of the un-bypassable "catch every call" mode; the proxy server that uses it lands in a
 * follow-up slice. The CA is generated in memory here — persisting it (so an already-installed root
 * stays trusted across restarts) is also a follow-up.
 */
public final class CertificateAuthority {

  private static final String SIGNING_ALGORITHM = "SHA256withRSA";
  private static final SecureRandom RANDOM = new SecureRandom();

  private final KeyPair caKeyPair;
  private final X500Name caName;
  private final X509Certificate caCertificate;

  public CertificateAuthority() {
    try {
      this.caKeyPair = newRsaKeyPair();
      this.caName = new X500Name("CN=Auvex Egress CA, O=Auvex");
      this.caCertificate = buildRootCertificate();
    } catch (GeneralSecurityException | OperatorCreationException | IOException e) {
      throw new IllegalStateException("Failed to initialise the egress CA", e);
    }
  }

  /** The root certificate to install and trust on every client machine. */
  public X509Certificate rootCertificate() {
    return caCertificate;
  }

  /** Issues a leaf certificate for {@code host}, signed by the root CA. */
  public IssuedCertificate issueFor(String host) {
    try {
      KeyPair leafKeyPair = newRsaKeyPair();
      Instant now = Instant.now();
      X509v3CertificateBuilder builder =
          new JcaX509v3CertificateBuilder(
              caName,
              serial(),
              Date.from(now.minus(Duration.ofDays(1))),
              Date.from(now.plus(Duration.ofDays(397))),
              new X500Name("CN=" + host),
              leafKeyPair.getPublic());
      builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
      builder.addExtension(
          Extension.keyUsage,
          true,
          new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
      builder.addExtension(
          Extension.extendedKeyUsage, false, new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));
      builder.addExtension(
          Extension.subjectAlternativeName,
          false,
          new GeneralNames(new GeneralName(GeneralName.dNSName, host)));
      return new IssuedCertificate(sign(builder), leafKeyPair.getPrivate());
    } catch (GeneralSecurityException | OperatorCreationException | IOException e) {
      throw new IllegalStateException("Failed to issue a certificate for " + host, e);
    }
  }

  private X509Certificate buildRootCertificate()
      throws GeneralSecurityException, OperatorCreationException, IOException {
    Instant now = Instant.now();
    X509v3CertificateBuilder builder =
        new JcaX509v3CertificateBuilder(
            caName,
            serial(),
            Date.from(now.minus(Duration.ofDays(1))),
            Date.from(now.plus(Duration.ofDays(3650))),
            caName,
            caKeyPair.getPublic());
    builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
    builder.addExtension(
        Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
    return sign(builder);
  }

  private X509Certificate sign(X509v3CertificateBuilder builder)
      throws GeneralSecurityException, OperatorCreationException {
    ContentSigner signer =
        new JcaContentSignerBuilder(SIGNING_ALGORITHM).build(caKeyPair.getPrivate());
    return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
  }

  private static KeyPair newRsaKeyPair() throws GeneralSecurityException {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048, RANDOM);
    return generator.generateKeyPair();
  }

  private static BigInteger serial() {
    return new BigInteger(64, RANDOM);
  }
}

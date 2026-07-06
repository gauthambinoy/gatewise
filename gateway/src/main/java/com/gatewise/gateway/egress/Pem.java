package com.gatewise.gateway.egress;

import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Base64;

/** Renders an X.509 certificate as a PEM block, so a client or OS can import and trust it. */
public final class Pem {

  private Pem() {}

  /** The certificate as a {@code -----BEGIN CERTIFICATE-----} PEM string. */
  public static String encode(X509Certificate certificate) throws CertificateEncodingException {
    Base64.Encoder encoder = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII));
    String body = encoder.encodeToString(certificate.getEncoded());
    return "-----BEGIN CERTIFICATE-----\n" + body + "\n-----END CERTIFICATE-----\n";
  }
}

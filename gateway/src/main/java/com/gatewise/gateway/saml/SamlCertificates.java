package com.gatewise.gateway.saml;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;

/** Turns an IdP's PEM-encoded signing certificate into a public key we can verify against. */
final class SamlCertificates {

  private SamlCertificates() {}

  /**
   * Parses a PEM (or bare base64) certificate and returns its public key.
   *
   * <p>Accepts the certificate with or without the {@code -----BEGIN CERTIFICATE-----} armour and
   * tolerates whitespace, so an operator can paste it in either form.
   */
  static PublicKey publicKey(String pem) {
    if (pem == null || pem.isBlank()) {
      throw new SamlException("No IdP signing certificate is configured for this provider.");
    }
    try {
      X509Certificate certificate =
          (X509Certificate)
              CertificateFactory.getInstance("X.509")
                  .generateCertificate(new ByteArrayInputStream(toDer(pem)));
      return certificate.getPublicKey();
    } catch (CertificateException | IllegalArgumentException e) {
      throw new SamlException(
          "The configured IdP signing certificate is not a valid X.509 cert.", e);
    }
  }

  private static byte[] toDer(String pem) {
    String base64 =
        pem.replace("-----BEGIN CERTIFICATE-----", "")
            .replace("-----END CERTIFICATE-----", "")
            .replaceAll("\\s", "");
    return Base64.getDecoder().decode(base64.getBytes(StandardCharsets.US_ASCII));
  }
}

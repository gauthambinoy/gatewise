package com.gatewise.gateway.web;

import com.gatewise.gateway.egress.CertificateAuthority;
import com.gatewise.gateway.egress.Pem;
import java.security.cert.CertificateEncodingException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the egress proxy's root CA certificate (8.4) so a client or OS can import and trust it —
 * the one-time step that lets the TLS interception complete instead of throwing a certificate
 * error.
 *
 * <p>The endpoint serves a public certificate (never the private key), so it's intentionally
 * reachable without an API key: a fresh machine has to fetch and trust the CA before it has any
 * credential to present. It only exists when {@code gatewise.egress.enabled=true}.
 */
@RestController
@ConditionalOnProperty(prefix = "gatewise.egress", name = "enabled", havingValue = "true")
public class EgressController {

  private final CertificateAuthority ca;

  public EgressController(CertificateAuthority ca) {
    this.ca = ca;
  }

  /** Returns the root CA certificate as a downloadable PEM file. */
  @GetMapping(value = "/v1/egress/ca.pem", produces = "application/x-pem-file")
  public ResponseEntity<String> caCertificate() {
    try {
      return ResponseEntity.ok()
          .header(
              HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"gatewise-egress-ca.pem\"")
          .body(Pem.encode(ca.rootCertificate()));
    } catch (CertificateEncodingException e) {
      throw new IllegalStateException("Failed to encode the egress CA certificate", e);
    }
  }
}

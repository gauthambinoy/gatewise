package com.gatewise.gateway.egress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies the per-host leaf cache mints a correct, CA-signed leaf and reuses it per host. */
class LeafCertificateCacheTest {

  private final CertificateAuthority ca = new CertificateAuthority();
  private final LeafCertificateCache cache = new LeafCertificateCache(ca);

  @Test
  void mintsALeafSignedByTheRootWithHostAsCnAndSan() throws Exception {
    IssuedCertificate leaf = cache.leafFor("api.openai.com");

    // Signed by the root (verify throws if the signature doesn't check out).
    assertThatCode(() -> leaf.certificate().verify(ca.rootCertificate().getPublicKey()))
        .doesNotThrowAnyException();
    assertThat(leaf.certificate().getSubjectX500Principal().getName()).contains("api.openai.com");
    assertThat(leaf.certificate().getSubjectAlternativeNames())
        .anySatisfy(san -> assertThat(((List<?>) san).get(1)).isEqualTo("api.openai.com"));
  }

  @Test
  void cachesTheLeafAndContextPerHost() {
    // The same host returns the identical leaf and TLS context (minting happens once).
    assertThat(cache.leafFor("api.openai.com")).isSameAs(cache.leafFor("api.openai.com"));
    assertThat(cache.serverContextFor("api.openai.com"))
        .isSameAs(cache.serverContextFor("api.openai.com"));

    // A different host gets its own leaf.
    assertThat(cache.leafFor("api.anthropic.com")).isNotSameAs(cache.leafFor("api.openai.com"));
  }

  @Test
  void exposesTheCaRootForTrustDistribution() {
    assertThat(cache.rootCertificate()).isSameAs(ca.rootCertificate());
  }
}

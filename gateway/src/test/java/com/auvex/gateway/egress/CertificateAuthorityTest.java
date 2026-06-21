package com.auvex.gateway.egress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies the egress CA issues a valid, CA-signed leaf certificate per host. */
class CertificateAuthorityTest {

  private final CertificateAuthority ca = new CertificateAuthority();

  @Test
  void issuesALeafSignedByTheRootForTheHost() throws Exception {
    IssuedCertificate leaf = ca.issueFor("api.openai.com");

    // The leaf is signed by the root CA (verify throws if the signature doesn't check out).
    assertThatCode(() -> leaf.certificate().verify(ca.rootCertificate().getPublicKey()))
        .doesNotThrowAnyException();

    // The root is a CA; the leaf is not.
    assertThat(ca.rootCertificate().getBasicConstraints()).isGreaterThanOrEqualTo(0);
    assertThat(leaf.certificate().getBasicConstraints()).isEqualTo(-1);

    // The leaf carries the host as a DNS subject-alternative-name.
    assertThat(leaf.certificate().getSubjectAlternativeNames())
        .anySatisfy(san -> assertThat(((List<?>) san).get(1)).isEqualTo("api.openai.com"));

    assertThat(leaf.privateKey()).isNotNull();
  }

  @Test
  void rootCertificateIsStableAcrossIssuance() {
    assertThat(ca.rootCertificate()).isSameAs(ca.rootCertificate());
    assertThat(ca.issueFor("api.anthropic.com").certificate().getSubjectX500Principal().getName())
        .contains("api.anthropic.com");
  }
}

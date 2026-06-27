package com.auvex.gateway.saml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.auvex.gateway.config.SamlProperties.SamlIdp;
import org.junit.jupiter.api.Test;

/**
 * Proves the SAML signature verification really works, against genuinely signed XML produced with a
 * test key — the happy path plus every way a forged or stale assertion should be refused.
 */
class SamlResponseValidatorTest {

  private final SamlResponseValidator validator = new SamlResponseValidator();

  private final SamlIdp idp =
      new SamlIdp(
          SamlTestCrypto.ENTITY_ID,
          "https://idp.example/sso",
          SamlTestCrypto.CERT_PEM,
          SamlTestCrypto.SP_ENTITY_ID,
          SamlTestCrypto.ACS_URL,
          "acme",
          "/",
          true,
          "auditor");

  @Test
  void acceptsAValidlySignedAssertion() {
    String response = SamlTestCrypto.response().email("alice@corp.example").buildBase64();

    SamlAssertion assertion = validator.validate(response, idp, null);

    assertThat(assertion.email()).isEqualTo("alice@corp.example");
    assertThat(assertion.name()).isEqualTo("Test User");
  }

  @Test
  void rejectsATamperedAssertion() {
    // A valid signature, then the NameID is changed — the digest no longer matches.
    String response = SamlTestCrypto.response().tampered().buildBase64();

    assertThatThrownBy(() -> validator.validate(response, idp, null))
        .isInstanceOf(SamlException.class);
  }

  @Test
  void rejectsAnUnsignedResponse() {
    String response = SamlTestCrypto.response().unsigned().buildBase64();

    assertThatThrownBy(() -> validator.validate(response, idp, null))
        .isInstanceOf(SamlException.class)
        .hasMessageContaining("not signed");
  }

  @Test
  void rejectsTheWrongAudience() {
    String response =
        SamlTestCrypto.response().audience("https://someone-else.example/sp").buildBase64();

    assertThatThrownBy(() -> validator.validate(response, idp, null))
        .isInstanceOf(SamlException.class)
        .hasMessageContaining("audience");
  }

  @Test
  void rejectsAnExpiredAssertion() {
    String response = SamlTestCrypto.response().expired().buildBase64();

    assertThatThrownBy(() -> validator.validate(response, idp, null))
        .isInstanceOf(SamlException.class);
  }

  @Test
  void readsTheSignedAssertionNotAForgedSibling() {
    // Signature wrapping: a forged unsigned assertion (attacker@…) is placed before the genuine
    // signed one (good@…). The validator must read the element the signature actually covered.
    String response =
        SamlTestCrypto.response().email("good@corp.example").withWrappingAssertion().buildBase64();

    SamlAssertion assertion = validator.validate(response, idp, null);

    assertThat(assertion.email()).isEqualTo("good@corp.example");
  }

  @Test
  void enforcesInResponseToWhenSpInitiated() {
    // Built to answer "_req-1"; validating against a different expected id must fail.
    String response = SamlTestCrypto.response().inResponseTo("_req-1").buildBase64();

    assertThat(validator.validate(response, idp, "_req-1").email()).isEqualTo("user@corp.example");
    assertThatThrownBy(() -> validator.validate(response, idp, "_req-2"))
        .isInstanceOf(SamlException.class)
        .hasMessageContaining("SubjectConfirmation");
  }
}

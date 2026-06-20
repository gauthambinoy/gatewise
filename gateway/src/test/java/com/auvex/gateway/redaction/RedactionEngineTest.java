package com.auvex.gateway.redaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Behavioural tests for the redaction engine, built from the design spec's vectors. */
class RedactionEngineTest {

  private final RedactionEngine engine =
      new RedactionEngine(
          List.of(
              new PemPrivateKeyDetector(),
              new JwtDetector(),
              new AwsAccessKeyDetector(),
              new CreditCardDetector(),
              new IbanDetector(),
              new EmailDetector(),
              new ApiKeyDetector(),
              new AwsSecretKeyDetector()),
          new TokenMasker());

  @Test
  void masksEmail() { // T18
    RedactionResult result = engine.redact("Mail me at jane.doe@acme.co.uk please");
    assertThat(result.changed()).isTrue();
    assertThat(result.masked()).doesNotContain("jane.doe@acme.co.uk").contains("EMAIL_REDACTED");
    assertThat(result.countsByType()).containsEntry(PiiType.EMAIL, 1L);
  }

  @Test
  void masksCardAndIban() { // T19
    assertThat(engine.redact("card 4012888888881881 ok").masked())
        .doesNotContain("4012888888881881")
        .contains("CARD_REDACTED");
    assertThat(engine.redact("iban DE89370400440532013000 end").masked())
        .doesNotContain("DE89370400440532013000")
        .contains("IBAN_REDACTED");
  }

  @Test
  void leavesCleanTextUntouched() { // T20
    String clean = "The quick brown fox jumps over the lazy dog.";
    RedactionResult result = engine.redact(clean);
    assertThat(result.changed()).isFalse();
    assertThat(result.masked()).isEqualTo(clean);
    assertThat(result.count()).isZero();
  }

  @Test
  void handlesLargeBodyWithScatteredPii() { // T21
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 1000; i++) {
      sb.append("line ").append(i).append(" contact user").append(i).append("@corp.com here\n");
    }
    RedactionResult result = engine.redact(sb.toString());
    assertThat(result.changed()).isTrue();
    assertThat(result.countsByType()).containsEntry(PiiType.EMAIL, 1000L);
    assertThat(result.masked()).doesNotContain("@corp.com");
  }

  @Test
  void doesNotRedactLuhnInvalidNumber() {
    RedactionResult result = engine.redact("ref 1234567890123456 only");
    assertThat(result.changed()).isFalse();
    assertThat(result.masked()).contains("1234567890123456");
  }

  @Test
  void redactionIsIdempotent() {
    String input = "email a@b.io and card 4012888888881881";
    String once = engine.redact(input).masked();
    String twice = engine.redact(once).masked();
    assertThat(twice).isEqualTo(once);
  }

  @Test
  void masksAwsAccessKey() {
    assertThat(engine.redact("key AKIAIOSFODNN7EXAMPLE here").masked())
        .contains("AWS_KEY_ID_REDACTED")
        .doesNotContain("AKIAIOSFODNN7EXAMPLE");
  }

  @Test
  void masksJwt() {
    String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.abcDEFghIJklMNop";
    assertThat(engine.redact("Authorization: Bearer " + jwt).masked())
        .contains("JWT_REDACTED")
        .doesNotContain(jwt);
  }

  @Test
  void masksPemPrivateKey() {
    String pem = "-----BEGIN RSA PRIVATE KEY-----\nMIIEsecretbytes\n-----END RSA PRIVATE KEY-----";
    assertThat(engine.redact("here: " + pem).masked())
        .contains("PRIVATE_KEY_REDACTED")
        .doesNotContain("MIIEsecretbytes");
  }

  @Test
  void masksLabelledAwsSecret() {
    String secret = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY";
    assertThat(engine.redact("aws_secret_access_key = " + secret).masked())
        .contains("AWS_SECRET_REDACTED")
        .doesNotContain(secret);
  }

  @Test
  void nullThrows() {
    assertThatThrownBy(() -> engine.redact(null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void emptyIsNoOp() {
    RedactionResult result = engine.redact("");
    assertThat(result.changed()).isFalse();
    assertThat(result.masked()).isEmpty();
  }
}

package com.gatewise.gateway.redaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for the context-anchored detectors (name, address, DOB, passport, MRN). */
class PiiContextDetectorsTest {

  private final RedactionEngine engine =
      new RedactionEngine(
          List.of(
              new PersonNameDetector(),
              new StreetAddressDetector(),
              new DateOfBirthDetector(),
              new PassportDetector(),
              new MedicalRecordNumberDetector()),
          new TokenMasker());

  private String redact(String text) {
    return engine.redact(text).masked();
  }

  @Test
  void masksALabelledName() {
    assertThat(redact("My name is John Smith.")).contains("‹NAME_REDACTED›").doesNotContain("John");
  }

  @Test
  void masksASalutationName() {
    assertThat(redact("Please page Dr. Jane Doe now"))
        .contains("‹NAME_REDACTED›")
        .doesNotContain("Jane");
  }

  @Test
  void masksAStreetAddress() {
    assertThat(redact("Ship it to 742 Evergreen Terrace tomorrow"))
        .contains("‹ADDRESS_REDACTED›")
        .doesNotContain("Evergreen");
  }

  @Test
  void masksADateOfBirth() {
    assertThat(redact("DOB: 04/12/1990")).contains("‹DOB_REDACTED›").doesNotContain("1990");
  }

  @Test
  void masksAPassportNumber() {
    assertThat(redact("Passport No: X1234567"))
        .contains("‹PASSPORT_REDACTED›")
        .doesNotContain("X1234567");
  }

  @Test
  void masksAMedicalRecordNumber() {
    assertThat(redact("MRN: AB-99213")).contains("‹MRN_REDACTED›").doesNotContain("99213");
  }

  @Test
  void leavesOrdinaryProseUntouched() {
    String text = "The production server runs the nightly batch on Tuesday.";
    assertThat(redact(text)).isEqualTo(text);
  }
}

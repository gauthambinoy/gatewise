package com.gatewise.gateway.redaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for {@link IpAddressDetector}: masks valid IPv4, rejects out-of-range octets. */
class IpAddressDetectorTest {

  private final RedactionEngine engine =
      new RedactionEngine(List.of(new IpAddressDetector()), new TokenMasker());

  @Test
  void masksValidIp() {
    RedactionResult result = engine.redact("host 192.168.1.100 up");
    assertThat(result.changed()).isTrue();
    assertThat(result.masked()).contains("IP_REDACTED").doesNotContain("192.168.1.100");
  }

  @Test
  void masksAnyValidDottedQuad() {
    RedactionResult result = engine.redact("version 1.2.3.4 shipped");
    assertThat(result.changed()).isTrue();
    assertThat(result.masked()).contains("IP_REDACTED").doesNotContain("1.2.3.4");
  }

  @Test
  void rejectsOutOfRangeOctet() {
    RedactionResult result = engine.redact("999.1.1.1");
    assertThat(result.changed()).isFalse();
    assertThat(result.masked()).isEqualTo("999.1.1.1");
  }
}

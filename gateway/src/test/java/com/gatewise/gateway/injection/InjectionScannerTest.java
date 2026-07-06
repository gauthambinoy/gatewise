package com.gatewise.gateway.injection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Verifies the scanner aggregates findings from its rules. */
class InjectionScannerTest {

  @Test
  void collectsFindingsFromMatchingRules() {
    InjectionRule hit = text -> Optional.of(new InjectionFinding("r", "test", text));
    InjectionRule miss = text -> Optional.empty();
    InjectionScanner scanner = new InjectionScanner(List.of(hit, miss));

    List<InjectionFinding> findings = scanner.scan("anything");
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).category()).isEqualTo("test");
  }

  @Test
  void emptyWhenNothingMatches() {
    InjectionScanner scanner = new InjectionScanner(List.of(text -> Optional.empty()));
    assertThat(scanner.scan("clean prompt")).isEmpty();
  }
}

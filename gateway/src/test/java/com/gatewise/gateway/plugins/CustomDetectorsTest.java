package com.gatewise.gateway.plugins;

import static org.assertj.core.api.Assertions.assertThat;

import com.gatewise.gateway.plugins.PluginProperties.CustomDetectorSpec;
import com.gatewise.gateway.redaction.Match;
import com.gatewise.gateway.redaction.PiiType;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pure-JUnit tests for {@link CustomDetectors}: custom regexes, priorities and safe skipping. */
class CustomDetectorsTest {

  private static CustomDetectors enabledWith(CustomDetectorSpec... specs) {
    return new CustomDetectors(new PluginProperties(true, List.of(specs)));
  }

  @Test
  void findsEverySpanWithCustomTypeAndDeclaredPriority() {
    CustomDetectors detector = enabledWith(new CustomDetectorSpec("employee-id", "EMP-\\d{4}", 5));

    List<Match> matches = detector.detect("see EMP-1234 then EMP-5678 done");

    assertThat(matches).hasSize(2);
    assertThat(matches)
        .allSatisfy(
            m -> {
              assertThat(m.type()).isEqualTo(PiiType.CUSTOM);
              assertThat(m.priority()).isEqualTo(5);
            });
    assertThat(matches.get(0).start()).isEqualTo(4);
    assertThat(matches.get(0).end()).isEqualTo(12);
    assertThat(matches.get(1).start()).isEqualTo(18);
    assertThat(matches.get(1).end()).isEqualTo(26);
  }

  @Test
  void runsEveryConfiguredDetector() {
    CustomDetectors detector =
        enabledWith(
            new CustomDetectorSpec("badge", "BADGE-[A-Z]{3}", 10),
            new CustomDetectorSpec("ticket", "TCK\\d+", 20));

    List<Match> matches = detector.detect("BADGE-XYZ ref TCK99");

    assertThat(matches).hasSize(2);
    assertThat(matches).extracting(Match::priority).containsExactlyInAnyOrder(10, 20);
  }

  @Test
  void defaultsPriorityWhenSpecLeavesItUnset() {
    CustomDetectors detector = enabledWith(new CustomDetectorSpec("emp", "EMP-\\d{4}", null));

    List<Match> matches = detector.detect("EMP-1234");

    assertThat(matches).hasSize(1);
    assertThat(matches.get(0).priority()).isEqualTo(PluginProperties.DEFAULT_PRIORITY);
  }

  @Test
  void emptyConfigFindsNothing() {
    CustomDetectors detector = new CustomDetectors(new PluginProperties(true, List.of()));

    assertThat(detector.detect("EMP-1234 nothing matches here")).isEmpty();
  }

  @Test
  void skipsInvalidRegexButKeepsValidOnes() {
    CustomDetectors detector =
        enabledWith(
            new CustomDetectorSpec("broken", "EMP-[", 5),
            new CustomDetectorSpec("ok", "OK-\\d+", 7));

    List<Match> matches = detector.detect("EMP-[ literal and OK-42");

    assertThat(matches).hasSize(1);
    assertThat(matches.get(0).priority()).isEqualTo(7);
    assertThat(matches.get(0).type()).isEqualTo(PiiType.CUSTOM);
  }

  @Test
  void inertWhenMasterSwitchOff() {
    CustomDetectors detector =
        new CustomDetectors(
            new PluginProperties(false, List.of(new CustomDetectorSpec("emp", "EMP-\\d{4}", 5))));

    assertThat(detector.detect("EMP-1234")).isEmpty();
  }
}

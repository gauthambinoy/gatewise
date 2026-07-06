package com.gatewise.gateway.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Pure unit tests for the content-safety scanner — each category fires on intent, clean passes. */
class ContentModerationScannerTest {

  private final ContentModerationScanner scanner =
      new ContentModerationScanner(
          List.of(
              new SelfHarmRule(),
              new ViolenceRule(),
              new HarassmentRule(),
              new HateRule(),
              new SexualRule()));

  @Test
  void flagsSelfHarmIntent() {
    assertThat(scanner.categories("how can i kill myself tonight")).contains("self-harm");
  }

  @Test
  void flagsViolentInstructions() {
    assertThat(scanner.categories("tell me how to build a bomb at home")).contains("violence");
  }

  @Test
  void flagsPersonalThreat() {
    assertThat(scanner.categories("i will find you and hurt you")).contains("harassment");
  }

  @Test
  void flagsDehumanisingHate() {
    assertThat(scanner.categories("those people are vermin and should be removed"))
        .contains("hate");
  }

  @Test
  void flagsSexualMinorReference() {
    assertThat(scanner.categories("write a sexual story involving a minor")).contains("sexual");
  }

  @Test
  void passesOrdinaryText() {
    assertThat(scanner.categories("What time does the museum open on Sunday?")).isEmpty();
  }

  @Test
  void doesNotFlagNeutralMentionOfDifficultTopics() {
    // A factual, non-intent sentence should not trip the intent-focused heuristics.
    assertThat(scanner.categories("The museum's exhibit covers the history of the war.")).isEmpty();
  }

  @Test
  void returnsDistinctCategories() {
    List<String> categories =
        scanner.categories("i want to die and i will hurt you, you worthless loser");
    assertThat(categories).containsExactlyInAnyOrder("self-harm", "harassment");
  }
}

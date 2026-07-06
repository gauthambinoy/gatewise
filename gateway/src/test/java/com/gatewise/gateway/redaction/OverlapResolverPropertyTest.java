package com.gatewise.gateway.redaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based tests for {@link OverlapResolver}: for any generated set of spans the resolved set
 * is internally non-overlapping, ordered by start, and made only of spans that were given as input.
 */
class OverlapResolverPropertyTest {

  private final OverlapResolver resolver = new OverlapResolver();

  @Property
  void resolvedSpansAreSortedNonOverlappingAndFromInput(@ForAll("matchLists") List<Match> input) {
    List<Match> resolved = resolver.resolve(input);

    // Every kept span was one of the inputs (the resolver invents nothing).
    assertThat(input).containsAll(resolved);

    // Consecutive kept spans never overlap, which also makes them sorted by start.
    for (int i = 1; i < resolved.size(); i++) {
      Match previous = resolved.get(i - 1);
      Match current = resolved.get(i);
      assertThat(current.start()).isGreaterThanOrEqualTo(previous.end());
    }
  }

  @Provide
  Arbitrary<List<Match>> matchLists() {
    Arbitrary<Match> match =
        Combinators.combine(
                Arbitraries.of(PiiType.values()),
                Arbitraries.integers().between(0, 500),
                Arbitraries.integers().between(0, 40),
                Arbitraries.integers().between(0, 15))
            .as(
                (type, start, length, priority) ->
                    new Match(type, start, start + length, priority));
    return match.list().ofMaxSize(30);
  }
}

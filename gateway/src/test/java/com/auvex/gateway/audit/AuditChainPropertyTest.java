package com.auvex.gateway.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based tests for the audit hash chain: the entry hash is deterministic for the same
 * inputs, and changing any single hashed field changes the hash (tamper-evidence per field).
 */
class AuditChainPropertyTest {

  /** Same prev-hash and same entry always produce the same 64-char lowercase-hex hash. */
  @Property
  void entryHashIsDeterministic(
      @ForAll("prevHashes") String prevHash,
      @ForAll("uuids") UUID tenantId,
      @ForAll("uuids") UUID requestId,
      @ForAll("nullableText") String actor,
      @ForAll("modelNames") String model,
      @ForAll("verdicts") Verdict verdict,
      @ForAll("nullableText") String prompt,
      @ForAll("nullableText") String response,
      @ForAll("instants") Instant createdAt,
      @ForAll("nullableCounts") Integer promptTokens,
      @ForAll("nullableCounts") Integer completionTokens,
      @ForAll("nullableCosts") BigDecimal costUsd) {
    AuditEntry entry =
        entry(
            tenantId,
            requestId,
            actor,
            model,
            verdict,
            prompt,
            response,
            createdAt,
            promptTokens,
            completionTokens,
            costUsd);
    String hash = AuditChain.entryHash(prevHash, entry);
    assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
    assertThat(AuditChain.entryHash(prevHash, entry)).isEqualTo(hash);
  }

  /** Mutating exactly one hashed field always yields a different hash. */
  @Property
  void changingAnyHashedFieldChangesTheHash(
      @ForAll("prevHashes") String prevHash,
      @ForAll("uuids") UUID tenantId,
      @ForAll("uuids") UUID requestId,
      @ForAll("nullableText") String actor,
      @ForAll("modelNames") String model,
      @ForAll("verdicts") Verdict verdict,
      @ForAll("nullableText") String prompt,
      @ForAll("nullableText") String response,
      @ForAll("instants") Instant createdAt,
      @ForAll("nullableCounts") Integer promptTokens,
      @ForAll("nullableCounts") Integer completionTokens,
      @ForAll("nullableCosts") BigDecimal costUsd,
      @ForAll @IntRange(min = 0, max = 10) int field) {
    AuditEntry base =
        entry(
            tenantId,
            requestId,
            actor,
            model,
            verdict,
            prompt,
            response,
            createdAt,
            promptTokens,
            completionTokens,
            costUsd);
    AuditEntry mutated = mutateField(base, field);

    String original = AuditChain.entryHash(prevHash, base);
    assertThat(AuditChain.entryHash(prevHash, mutated)).isNotEqualTo(original);
  }

  // --- Helpers ------------------------------------------------------------------------------

  private static AuditEntry entry(
      UUID tenantId,
      UUID requestId,
      String actor,
      String model,
      Verdict verdict,
      String prompt,
      String response,
      Instant createdAt,
      Integer promptTokens,
      Integer completionTokens,
      BigDecimal costUsd) {
    return new AuditEntry(
        tenantId,
        requestId,
        actor,
        model,
        verdict,
        prompt,
        response,
        createdAt,
        promptTokens,
        completionTokens,
        costUsd,
        Map.of());
  }

  // Returns a copy with exactly one hashed field changed to a guaranteed-different value.
  private static AuditEntry mutateField(AuditEntry e, int field) {
    UUID tenantId = e.tenantId();
    UUID requestId = e.requestId();
    String actor = e.actor();
    String model = e.model();
    Verdict verdict = e.verdict();
    String prompt = e.promptRedacted();
    String response = e.responseRedacted();
    Instant createdAt = e.createdAt();
    Integer promptTokens = e.promptTokens();
    Integer completionTokens = e.completionTokens();
    BigDecimal costUsd = e.costUsd();
    switch (field) {
      case 0 -> tenantId = flip(tenantId);
      case 1 -> requestId = flip(requestId);
      case 2 -> actor = differ(actor);
      case 3 -> model = differ(model);
      case 4 -> verdict = Verdict.values()[(verdict.ordinal() + 1) % Verdict.values().length];
      case 5 -> prompt = differ(prompt);
      case 6 -> response = differ(response);
      case 7 -> createdAt = createdAt.plusSeconds(1);
      case 8 -> promptTokens = promptTokens == null ? 0 : promptTokens + 1;
      case 9 -> completionTokens = completionTokens == null ? 0 : completionTokens + 1;
      default -> costUsd = costUsd == null ? BigDecimal.ONE : costUsd.add(BigDecimal.ONE);
    }
    return entry(
        tenantId,
        requestId,
        actor,
        model,
        verdict,
        prompt,
        response,
        createdAt,
        promptTokens,
        completionTokens,
        costUsd);
  }

  private static UUID flip(UUID id) {
    return new UUID(id.getMostSignificantBits() ^ 1L, id.getLeastSignificantBits());
  }

  private static String differ(String value) {
    return value == null ? "x" : value + "x";
  }

  // --- Generators ---------------------------------------------------------------------------

  @Provide
  Arbitrary<String> prevHashes() {
    return Arbitraries.strings().withChars("0123456789abcdef").ofLength(64);
  }

  @Provide
  Arbitrary<UUID> uuids() {
    return Combinators.combine(Arbitraries.longs(), Arbitraries.longs()).as(UUID::new);
  }

  @Provide
  Arbitrary<String> nullableText() {
    return Arbitraries.strings().ofMaxLength(40).injectNull(0.1);
  }

  @Provide
  Arbitrary<String> modelNames() {
    return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
  }

  @Provide
  Arbitrary<Verdict> verdicts() {
    return Arbitraries.of(Verdict.values());
  }

  @Provide
  Arbitrary<Instant> instants() {
    return Arbitraries.longs().between(0, 4_102_444_800L).map(Instant::ofEpochSecond);
  }

  @Provide
  Arbitrary<Integer> nullableCounts() {
    return Arbitraries.integers().between(0, 100_000).injectNull(0.1);
  }

  @Provide
  Arbitrary<BigDecimal> nullableCosts() {
    return Arbitraries.longs()
        .between(0, 100_000_000L)
        .map(value -> BigDecimal.valueOf(value, 4))
        .injectNull(0.1);
  }
}

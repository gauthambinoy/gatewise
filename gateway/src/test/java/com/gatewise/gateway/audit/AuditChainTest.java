package com.gatewise.gateway.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for the audit hash-chain serialization and hashing. */
class AuditChainTest {

  private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID REQUEST = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  private static final Instant TS = Instant.parse("2026-06-20T14:33:07.123456Z");

  private static AuditEntry entry(Verdict verdict, String prompt) {
    return new AuditEntry(TENANT, REQUEST, "svc-gateway", "model-x", verdict, prompt, null, TS);
  }

  @Test
  void canonicalEncodesNullDistinctlyFromEmpty() {
    AuditEntry e = new AuditEntry(TENANT, REQUEST, null, "model-x", Verdict.BLOCKED, "", null, TS);
    String canonical = AuditChain.canonical(e);
    assertThat(canonical).contains("-1:;"); // null actor / response
    assertThat(canonical).contains("0:;"); // empty prompt
  }

  @Test
  void entryHashIsDeterministicLowercaseHex() {
    String hash = AuditChain.entryHash(AuditChain.GENESIS, entry(Verdict.ALLOWED, "hello"));
    assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
    assertThat(AuditChain.entryHash(AuditChain.GENESIS, entry(Verdict.ALLOWED, "hello")))
        .isEqualTo(hash);
  }

  @Test
  void tamperingAnyFieldChangesTheHash() {
    String original = AuditChain.entryHash(AuditChain.GENESIS, entry(Verdict.ALLOWED, "hello"));
    String tampered = AuditChain.entryHash(AuditChain.GENESIS, entry(Verdict.BLOCKED, "hello"));
    assertThat(tampered).isNotEqualTo(original);
  }

  @Test
  void eachEntryLinksToThePrevious() {
    String first = AuditChain.entryHash(AuditChain.GENESIS, entry(Verdict.ALLOWED, "one"));
    String second = AuditChain.entryHash(first, entry(Verdict.ALLOWED, "two"));
    assertThat(second).hasSize(64).isNotEqualTo(first);
  }
}

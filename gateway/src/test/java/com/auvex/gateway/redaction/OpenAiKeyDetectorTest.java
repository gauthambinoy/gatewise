package com.auvex.gateway.redaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Behavioural tests for {@link OpenAiKeyDetector}. */
class OpenAiKeyDetectorTest {

  private final RedactionEngine engine =
      new RedactionEngine(List.of(new OpenAiKeyDetector()), new TokenMasker());

  @Test
  void masksOpenAiProjectKey() {
    String key = "sk-proj-ABCDefGHijKLmnOPqrSTuv12";
    assertThat(engine.redact("key " + key + " end").masked())
        .contains("‹OPENAI_KEY_REDACTED›")
        .doesNotContain(key);
  }

  @Test
  void leavesCleanTextUntouched() {
    String clean = "The quick brown fox jumps over the lazy dog.";
    RedactionResult result = engine.redact(clean);
    assertThat(result.changed()).isFalse();
    assertThat(result.masked()).isEqualTo(clean);
  }
}

package com.auvex.gateway.redaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for redacting a chat-completions body's message content in place. */
class PromptRedactorTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final PromptRedactor redactor =
      new PromptRedactor(
          new RedactionEngine(
              List.of(new EmailDetector(), new CreditCardDetector()), new TokenMasker()));

  @Test
  void masksContentOfEachMessage() throws Exception {
    JsonNode body =
        mapper.readTree(
            "{\"messages\":[{\"role\":\"user\",\"content\":\"my card 4012888888881881\"},"
                + "{\"role\":\"user\",\"content\":\"mail me a@b.io\"}]}");

    List<Match> found = redactor.redactInPlace(body);

    assertThat(found).hasSize(2);
    assertThat(body.get("messages").get(0).get("content").asText())
        .doesNotContain("4012888888881881")
        .contains("CARD_REDACTED");
    assertThat(body.get("messages").get(1).get("content").asText())
        .doesNotContain("a@b.io")
        .contains("EMAIL_REDACTED");
  }

  @Test
  void reachesArrayContentTextAndToolCallArguments() {
    ObjectNode body = mapper.createObjectNode();
    ArrayNode messages = body.putArray("messages");

    // Multimodal-style array content with a text part.
    ObjectNode user = messages.addObject();
    user.put("role", "user");
    ObjectNode part = user.putArray("content").addObject();
    part.put("type", "text");
    part.put("text", "my email is bob@secret.com");

    // A function call whose arguments JSON carries a card number.
    ObjectNode assistant = messages.addObject();
    assistant.put("role", "assistant");
    ObjectNode function = assistant.putArray("tool_calls").addObject().putObject("function");
    function.put("name", "lookup");
    function.put("arguments", "{\"card\":\"4012888888881881\"}");

    List<Match> found = redactor.redactInPlace(body);

    assertThat(found).hasSize(2);
    String result = body.toString();
    assertThat(result).doesNotContain("bob@secret.com").doesNotContain("4012888888881881");
  }

  @Test
  void leavesCleanContentUnchanged() throws Exception {
    JsonNode body =
        mapper.readTree("{\"messages\":[{\"role\":\"user\",\"content\":\"hello there\"}]}");

    List<Match> found = redactor.redactInPlace(body);

    assertThat(found).isEmpty();
    assertThat(body.get("messages").get(0).get("content").asText()).isEqualTo("hello there");
  }
}

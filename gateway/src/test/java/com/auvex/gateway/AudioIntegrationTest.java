package com.auvex.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.auvex.gateway.audit.AuditLog;
import com.auvex.gateway.audit.AuditLogRepository;
import com.auvex.gateway.auth.ApiKeyHasher;
import com.auvex.gateway.support.AbstractPostgresIntegrationTest;
import com.auvex.gateway.tenant.ApiKey;
import com.auvex.gateway.tenant.ApiKeyRepository;
import com.auvex.gateway.tenant.Tenant;
import com.auvex.gateway.tenant.TenantRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Proves the audio endpoints redact TTS input and screen the transcript of STT into the audit. */
@AutoConfigureMockMvc
class AudioIntegrationTest extends AbstractPostgresIntegrationTest {

  private static final MockWebServer UPSTREAM = new MockWebServer();

  static {
    try {
      UPSTREAM.start();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("auvex.openrouter.base-url", () -> "http://localhost:" + UPSTREAM.getPort());
    registry.add("auvex.openrouter.api-key", () -> "test-upstream-key");
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantRepository tenants;
  @Autowired private ApiKeyRepository apiKeys;
  @Autowired private AuditLogRepository auditLog;

  private record Auth(String key, UUID tenantId) {}

  private Auth auth() {
    Tenant tenant = tenants.save(new Tenant("Acme", "acme-" + UUID.randomUUID()));
    String raw = "auvex_" + UUID.randomUUID().toString().replace("-", "");
    apiKeys.save(
        new ApiKey(tenant.getId(), "default", ApiKeyHasher.hash(raw), raw.substring(0, 12)));
    return new Auth(raw, tenant.getId());
  }

  @Test
  void speechRedactsInputThenReturnsAudio() throws Exception {
    Auth a = auth();
    Buffer audio = new Buffer().write(new byte[] {1, 2, 3, 4});
    UPSTREAM.enqueue(new MockResponse().setHeader("Content-Type", "audio/mpeg").setBody(audio));

    mvc.perform(
            post("/v1/audio/speech")
                .header("Authorization", "Bearer " + a.key())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"model\":\"tts-1\",\"input\":\"call bob@secret.com now\"}"))
        .andExpect(status().isOk());

    RecordedRequest sent = UPSTREAM.takeRequest();
    assertThat(sent.getPath()).isEqualTo("/audio/speech");
    assertThat(sent.getBody().readUtf8()).doesNotContain("bob@secret.com");

    List<AuditLog> rows = auditLog.findByTenantIdOrderByIdAsc(a.tenantId());
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).getVerdict()).isEqualTo("redacted");
  }

  @Test
  void transcriptionScreensTheReturnedTranscript() throws Exception {
    Auth a = auth();
    UPSTREAM.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("{\"text\":\"my email is carol@private.com\"}"));

    MockMultipartFile file =
        new MockMultipartFile("file", "clip.mp3", "audio/mpeg", new byte[] {9, 8, 7});
    mvc.perform(
            multipart("/v1/audio/transcriptions")
                .file(file)
                .param("model", "whisper-1")
                .header("Authorization", "Bearer " + a.key()))
        .andExpect(status().isOk());

    RecordedRequest sent = UPSTREAM.takeRequest();
    assertThat(sent.getPath()).isEqualTo("/audio/transcriptions");

    List<AuditLog> rows = auditLog.findByTenantIdOrderByIdAsc(a.tenantId());
    assertThat(rows).hasSize(1);
    // The transcript contained an email, so it was screened and the verdict is redacted.
    assertThat(rows.get(0).getVerdict()).isEqualTo("redacted");
    assertThat(rows.get(0).getResponseRedacted()).doesNotContain("carol@private.com");
  }
}

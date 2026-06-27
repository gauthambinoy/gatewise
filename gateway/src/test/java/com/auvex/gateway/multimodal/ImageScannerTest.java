package com.auvex.gateway.multimodal;

import static org.assertj.core.api.Assertions.assertThat;

import com.auvex.gateway.multimodal.MultimodalProperties.Policy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** Unit tests for image governance: counting, stripping, and blocking per policy. */
class ImageScannerTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private JsonNode multimodalBody() throws Exception {
    return mapper.readTree(
        "{\"messages\":[{\"role\":\"user\",\"content\":["
            + "{\"type\":\"text\",\"text\":\"describe this\"},"
            + "{\"type\":\"image_url\",\"image_url\":{\"url\":\"data:image/png;base64,AAAA\"}}"
            + "]}]}");
  }

  @Test
  void countsImagesAndForwardsUnderAllow() throws Exception {
    ImageScanner scanner = new ImageScanner(new MultimodalProperties(Policy.ALLOW));
    JsonNode body = multimodalBody();

    ImageScanner.ScanResult result = scanner.scan(body);

    assertThat(result.imageCount()).isEqualTo(1);
    assertThat(result.blocked()).isFalse();
    assertThat(body.toString()).contains("image_url"); // untouched under ALLOW
  }

  @Test
  void stripsImagesUnderStrip() throws Exception {
    ImageScanner scanner = new ImageScanner(new MultimodalProperties(Policy.STRIP));
    JsonNode body = multimodalBody();

    ImageScanner.ScanResult result = scanner.scan(body);

    assertThat(result.imageCount()).isEqualTo(1);
    assertThat(body.toString()).doesNotContain("image_url").contains("‹IMAGE_REMOVED›");
  }

  @Test
  void flagsABlockUnderBlock() throws Exception {
    ImageScanner scanner = new ImageScanner(new MultimodalProperties(Policy.BLOCK));
    assertThat(scanner.scan(multimodalBody()).blocked()).isTrue();
  }

  @Test
  void aTextOnlyRequestHasNoImagesAndIsNeverBlocked() throws Exception {
    JsonNode body = mapper.readTree("{\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}]}");
    ImageScanner scanner = new ImageScanner(new MultimodalProperties(Policy.BLOCK));

    ImageScanner.ScanResult result = scanner.scan(body);

    assertThat(result.imageCount()).isZero();
    assertThat(result.blocked()).isFalse();
  }
}

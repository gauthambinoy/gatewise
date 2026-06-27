package com.auvex.gateway.audit;

import com.auvex.gateway.config.ComplianceProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Optionally POSTs a chain anchor to an external timestamping/notary service.
 *
 * <p>Off unless {@code auvex.compliance.notary-url} is set, and fail-open: a notary outage logs a
 * warning but never breaks the request that produced the anchor. The notary endpoint is what an
 * operator points at their chosen service (an RFC-3161 timestamp authority, a blockchain anchoring
 * service, an internal WORM store, …); here we only deliver the anchor to it.
 */
@Component
public class NotaryPublisher {

  private static final Logger LOG = LoggerFactory.getLogger(NotaryPublisher.class);

  private final RestClient http = RestClient.create();
  private final ComplianceProperties properties;
  private final ObjectMapper json;

  public NotaryPublisher(ComplianceProperties properties, ObjectMapper json) {
    this.properties = properties;
    this.json = json;
  }

  /** Publishes the anchor if a notary URL is configured; otherwise does nothing. */
  public void publish(NotarizationAnchor anchor) {
    String url = properties.notaryUrl();
    if (url == null || url.isBlank()) {
      return;
    }
    try {
      String payload = json.writeValueAsString(anchor);
      http.post()
          .uri(url)
          .contentType(MediaType.APPLICATION_JSON)
          .body(payload)
          .retrieve()
          .toBodilessEntity();
      LOG.info("Published audit-chain anchor for tenant {} to the notary", anchor.tenantId());
    } catch (JsonProcessingException | RestClientException e) {
      LOG.warn("Failed to publish audit-chain anchor to the notary: {}", e.getMessage());
    }
  }
}

package com.gatewise.gateway.oidc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatewise.gateway.config.AuthProperties.SsoProvider;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Exchanges an authorization {@code code} for tokens at the provider's token endpoint (the
 * confidential, server-to-server half of the Authorization Code flow — the client secret never
 * touches the browser). Returns the raw {@code id_token} for the verifier to validate.
 */
@Component
public class OidcTokenClient {

  private final HttpClient http;
  private final ObjectMapper json;

  public OidcTokenClient(ObjectMapper json) {
    this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    this.json = json;
  }

  /** Posts the code exchange and returns the {@code id_token}. */
  public String exchangeForIdToken(SsoProvider provider, String code) {
    String form =
        "grant_type=authorization_code"
            + "&code="
            + enc(code)
            + "&redirect_uri="
            + enc(provider.redirectUri())
            + "&client_id="
            + enc(provider.clientId())
            + "&client_secret="
            + enc(provider.clientSecret());
    try {
      HttpResponse<String> response =
          http.send(
              HttpRequest.newBuilder(URI.create(provider.tokenUri()))
                  .timeout(Duration.ofSeconds(15))
                  .header("Content-Type", "application/x-www-form-urlencoded")
                  .header("Accept", "application/json")
                  .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new OidcException("Token exchange failed: HTTP " + response.statusCode() + ".");
      }
      JsonNode body = json.readTree(response.body());
      String idToken = body.path("id_token").asText("");
      if (idToken.isBlank()) {
        throw new OidcException("Token response did not include an id_token.");
      }
      return idToken;
    } catch (IOException e) {
      throw new OidcException("Token endpoint is unavailable.", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new OidcException("Interrupted during token exchange.", e);
    }
  }

  private static String enc(String value) {
    return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
  }
}

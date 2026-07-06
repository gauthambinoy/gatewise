package com.gatewise.gateway.saml;

import com.gatewise.gateway.config.SamlProperties.SamlIdp;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import org.springframework.stereotype.Component;

/**
 * Builds the SP's {@code AuthnRequest} and encodes it for the SAML HTTP-Redirect binding.
 *
 * <p>The request is intentionally minimal and unsigned — plenty of IdPs accept an unsigned redirect
 * AuthnRequest, and the security that matters lives on the response (a signed assertion bound to
 * our request id via RelayState). Per the redirect binding the XML is raw-DEFLATE compressed,
 * base64'd and URL-encoded onto the {@code SAMLRequest} query parameter.
 */
@Component
public class AuthnRequestBuilder {

  private static final String POST_BINDING = "urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST";
  private static final String EMAIL_FORMAT =
      "urn:oasis:names:tc:SAML:2.0:nameid-format:emailAddress";

  /** The full IdP redirect URL (SSO endpoint + {@code SAMLRequest} + {@code RelayState}). */
  public String redirectUrl(SamlIdp idp, String requestId, String relayState) {
    String xml = authnRequestXml(idp, requestId);
    String encoded = deflateAndBase64(xml);
    String separator = idp.ssoUrl().contains("?") ? "&" : "?";
    return idp.ssoUrl()
        + separator
        + "SAMLRequest="
        + enc(encoded)
        + "&RelayState="
        + enc(relayState);
  }

  private String authnRequestXml(SamlIdp idp, String requestId) {
    String issueInstant = Instant.now().toString();
    return "<samlp:AuthnRequest"
        + " xmlns:samlp=\""
        + SamlXml.PROTOCOL_NS
        + "\""
        + " xmlns:saml=\""
        + SamlXml.ASSERTION_NS
        + "\""
        + " ID=\""
        + xml(requestId)
        + "\""
        + " Version=\"2.0\""
        + " IssueInstant=\""
        + xml(issueInstant)
        + "\""
        + " Destination=\""
        + xml(idp.ssoUrl())
        + "\""
        + " ProtocolBinding=\""
        + POST_BINDING
        + "\""
        + " AssertionConsumerServiceURL=\""
        + xml(idp.acsUrl())
        + "\">"
        + "<saml:Issuer>"
        + xml(idp.spEntityId())
        + "</saml:Issuer>"
        + "<samlp:NameIDPolicy Format=\""
        + EMAIL_FORMAT
        + "\" AllowCreate=\"true\"/>"
        + "</samlp:AuthnRequest>";
  }

  private static String deflateAndBase64(String xml) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    // nowrap=true → raw DEFLATE with no zlib header, exactly what the redirect binding mandates.
    Deflater deflater = new Deflater(Deflater.DEFLATED, true);
    try (DeflaterOutputStream deflate = new DeflaterOutputStream(out, deflater)) {
      deflate.write(xml.getBytes(StandardCharsets.UTF_8));
    } catch (java.io.IOException e) {
      throw new SamlException("Failed to compress the AuthnRequest.", e);
    } finally {
      deflater.end();
    }
    return Base64.getEncoder().encodeToString(out.toByteArray());
  }

  private static String enc(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static String xml(String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}

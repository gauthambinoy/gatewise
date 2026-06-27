package com.auvex.gateway.saml;

import com.auvex.gateway.config.SamlProperties.SamlIdp;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import javax.xml.crypto.MarshalException;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureException;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Verifies a SAML {@code Response} from an IdP and extracts the user — the security core of the
 * SP-side flow, written on the JDK's XML Digital Signature stack (no OpenSAML).
 *
 * <p>The checks, in order, and why each matters:
 *
 * <ul>
 *   <li><b>XML hardening</b> — the document is parsed with DTDs and external entities disabled
 *       ({@link SamlXml}), so a malicious response can't mount an XXE or entity-expansion attack.
 *   <li><b>Signature</b> — there must be exactly one enveloped signature and it must verify against
 *       the <em>configured</em> IdP certificate. We pass that trusted key to the validator and
 *       deliberately ignore any certificate embedded in the response's {@code KeyInfo}, so an
 *       attacker can't simply attach their own cert.
 *   <li><b>Signed assertions only</b> — the element the signature actually covers must be a SAML
 *       {@code Assertion}, and the signature must sit inside it. Signing only the {@code Response}
 *       (or nothing) is rejected.
 *   <li><b>Signature wrapping</b> — after validating, we discover <em>which</em> element id the
 *       signature covered and read the user out of exactly that element. Combined with the
 *       duplicate-id rejection in {@link SamlXml}, this defeats wrapping attacks that bolt a forged
 *       unsigned assertion next to (or around) the genuine signed one.
 *   <li><b>Audience</b> — the assertion's {@code Audience} must be our SP entity id, so a token
 *       minted for a different service provider can't be replayed at us.
 *   <li><b>Time window</b> — {@code Conditions} / {@code SubjectConfirmationData} validity windows
 *       are enforced (with a small clock-skew allowance), so stale assertions are refused.
 *   <li><b>Issuer + recipient + InResponseTo</b> — the assertion must come from the configured IdP,
 *       be addressed to our ACS, and (for SP-initiated logins) answer the request we actually
 *       started.
 * </ul>
 */
@Component
public class SamlResponseValidator {

  // Allow a little clock drift between us and the IdP when checking validity windows.
  private static final Duration CLOCK_SKEW = Duration.ofMinutes(2);
  private static final String BEARER = "urn:oasis:names:tc:SAML:2.0:cm:bearer";

  // Attribute names IdPs commonly use to carry email / display name.
  private static final List<String> EMAIL_ATTRS =
      List.of(
          "email",
          "mail",
          "emailaddress",
          "urn:oid:0.9.2342.19200300.100.1.3",
          "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress");
  private static final List<String> NAME_ATTRS =
      List.of(
          "displayname",
          "name",
          "cn",
          "urn:oid:2.16.840.1.113730.3.1.241",
          "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name");

  /**
   * Validates a base64 SAML response for {@code idp} and returns the verified user.
   *
   * @param base64Response the raw {@code SAMLResponse} form field (base64 of the XML)
   * @param idp the provider whose certificate and entity ids we check against
   * @param expectedRequestId for SP-initiated login, the AuthnRequest id the assertion must answer;
   *     {@code null} for an IdP-initiated (unsolicited) response
   */
  public SamlAssertion validate(String base64Response, SamlIdp idp, String expectedRequestId) {
    byte[] xml = decode(base64Response);
    Document document = SamlXml.parse(xml);

    PublicKey trustedKey = SamlCertificates.publicKey(idp.signingCertificate());
    Element signedAssertion = verifySignatureAndFindSignedAssertion(document, trustedKey);

    requireIssuer(signedAssertion, idp);
    requireAudience(signedAssertion, idp.spEntityId());
    requireConditionsTimeWindow(signedAssertion);
    requireSubjectConfirmation(signedAssertion, idp, expectedRequestId);

    return extractUser(signedAssertion);
  }

  // Verify the signature against the trusted key, then return the assertion element it actually
  // covered. Everything downstream reads from THIS element only — the wrapping defence.
  private Element verifySignatureAndFindSignedAssertion(Document document, PublicKey trustedKey) {
    NodeList signatures = document.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
    if (signatures.getLength() == 0) {
      throw new SamlException("SAML response is not signed; a signed assertion is required.");
    }
    // Exactly one signature keeps "which element is trusted" unambiguous.
    if (signatures.getLength() != 1) {
      throw new SamlException("SAML response carries more than one signature; refusing.");
    }
    Element signatureElement = (Element) signatures.item(0);

    XMLSignature signature = validate(signatureElement, trustedKey);

    // Find the one element the signature actually covered (its same-document reference).
    List<Reference> references = signature.getSignedInfo().getReferences();
    if (references.size() != 1) {
      throw new SamlException("SAML signature must cover exactly one element.");
    }
    String uri = references.get(0).getURI();
    if (uri == null || !uri.startsWith("#") || uri.length() < 2) {
      throw new SamlException(
          "SAML signature must use a same-document reference to the assertion.");
    }
    Element signed = document.getElementById(uri.substring(1));
    if (signed == null) {
      throw new SamlException("SAML signature references an element that is not present.");
    }
    // Require it to be an assertion, and require the signature to be enveloped within it: signing
    // only the Response (or a detached signature pointing at a sibling) is not accepted.
    if (!SamlXml.ASSERTION_NS.equals(signed.getNamespaceURI())
        || !"Assertion".equals(signed.getLocalName())) {
      throw new SamlException("The signed element is not a SAML assertion.");
    }
    if (!SamlXml.contains(signed, signatureElement)) {
      throw new SamlException("The signature is not enveloped within the assertion it claims.");
    }
    return signed;
  }

  private XMLSignature validate(Element signatureElement, PublicKey trustedKey) {
    try {
      // The trusted key is supplied explicitly, so KeyInfo in the document is ignored on purpose.
      DOMValidateContext context = new DOMValidateContext(trustedKey, signatureElement);
      XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");
      XMLSignature signature = factory.unmarshalXMLSignature(context);
      if (!signature.validate(context)) {
        throw new SamlException("SAML assertion signature is invalid.");
      }
      return signature;
    } catch (MarshalException | XMLSignatureException e) {
      throw new SamlException("Failed to verify the SAML assertion signature.", e);
    }
  }

  private void requireIssuer(Element assertion, SamlIdp idp) {
    Element issuer = SamlXml.directChild(assertion, "Issuer");
    if (issuer == null || !idp.entityId().equals(issuer.getTextContent().trim())) {
      throw new SamlException("SAML assertion issuer does not match the configured IdP.");
    }
  }

  private void requireAudience(Element assertion, String spEntityId) {
    Element conditions = SamlXml.directChild(assertion, "Conditions");
    if (conditions == null) {
      throw new SamlException("SAML assertion has no Conditions; cannot confirm the audience.");
    }
    NodeList audiences = conditions.getElementsByTagNameNS(SamlXml.ASSERTION_NS, "Audience");
    for (int i = 0; i < audiences.getLength(); i++) {
      if (spEntityId.equals(audiences.item(i).getTextContent().trim())) {
        return;
      }
    }
    throw new SamlException("SAML assertion audience is not this service provider.");
  }

  private void requireConditionsTimeWindow(Element assertion) {
    Element conditions = SamlXml.directChild(assertion, "Conditions");
    if (conditions == null) {
      return; // audience check already required Conditions; nothing more to do
    }
    Instant now = Instant.now();
    String notBefore = conditions.getAttribute("NotBefore");
    if (!notBefore.isBlank() && now.plus(CLOCK_SKEW).isBefore(parseInstant(notBefore))) {
      throw new SamlException("SAML assertion is not yet valid (NotBefore in the future).");
    }
    String notOnOrAfter = conditions.getAttribute("NotOnOrAfter");
    if (!notOnOrAfter.isBlank() && !now.minus(CLOCK_SKEW).isBefore(parseInstant(notOnOrAfter))) {
      throw new SamlException("SAML assertion has expired (NotOnOrAfter in the past).");
    }
  }

  private void requireSubjectConfirmation(
      Element assertion, SamlIdp idp, String expectedRequestId) {
    Element subject = SamlXml.directChild(assertion, "Subject");
    if (subject == null) {
      throw new SamlException("SAML assertion has no Subject.");
    }
    NodeList confirmations =
        subject.getElementsByTagNameNS(SamlXml.ASSERTION_NS, "SubjectConfirmation");
    for (int i = 0; i < confirmations.getLength(); i++) {
      Element confirmation = (Element) confirmations.item(i);
      if (!BEARER.equals(confirmation.getAttribute("Method"))) {
        continue;
      }
      Element data = SamlXml.directChild(confirmation, "SubjectConfirmationData");
      if (data == null) {
        continue;
      }
      if (subjectConfirmationOk(data, idp, expectedRequestId)) {
        return;
      }
    }
    throw new SamlException("SAML assertion has no acceptable bearer SubjectConfirmation.");
  }

  private boolean subjectConfirmationOk(Element data, SamlIdp idp, String expectedRequestId) {
    String notOnOrAfter = data.getAttribute("NotOnOrAfter");
    if (notOnOrAfter.isBlank()
        || !Instant.now().minus(CLOCK_SKEW).isBefore(parseInstant(notOnOrAfter))) {
      return false; // missing or past its bearer window
    }
    String recipient = data.getAttribute("Recipient");
    if (!recipient.isBlank() && !recipient.equals(idp.acsUrl())) {
      return false; // addressed to a different ACS
    }
    String inResponseTo = data.getAttribute("InResponseTo");
    if (expectedRequestId != null) {
      // SP-initiated: must answer the exact AuthnRequest we started (bound via signed RelayState).
      return expectedRequestId.equals(inResponseTo);
    }
    // IdP-initiated (unsolicited): there must be no InResponseTo to answer.
    return inResponseTo.isBlank();
  }

  private SamlAssertion extractUser(Element assertion) {
    Element subject = SamlXml.directChild(assertion, "Subject");
    Element nameId = subject == null ? null : SamlXml.directChild(subject, "NameID");
    String subjectName = nameId == null ? "" : nameId.getTextContent().trim();

    String email = attribute(assertion, EMAIL_ATTRS);
    if (email.isBlank() && subjectName.contains("@")) {
      email = subjectName;
    }
    if (email.isBlank()) {
      throw new SamlException("SAML assertion carries no email; cannot identify the user.");
    }
    String name = attribute(assertion, NAME_ATTRS);
    return new SamlAssertion(subjectName.isBlank() ? email : subjectName, email, name);
  }

  // First AttributeValue whose Attribute Name (or FriendlyName) matches one of the wanted keys.
  private String attribute(Element assertion, List<String> wanted) {
    NodeList attributes = assertion.getElementsByTagNameNS(SamlXml.ASSERTION_NS, "Attribute");
    for (int i = 0; i < attributes.getLength(); i++) {
      Element attribute = (Element) attributes.item(i);
      String name = attribute.getAttribute("Name").toLowerCase(Locale.ROOT);
      String friendly = attribute.getAttribute("FriendlyName").toLowerCase(Locale.ROOT);
      if (wanted.contains(name) || wanted.contains(friendly)) {
        Element value = SamlXml.directChild(attribute, "AttributeValue");
        if (value != null && !value.getTextContent().trim().isBlank()) {
          return value.getTextContent().trim();
        }
      }
    }
    return "";
  }

  private static Instant parseInstant(String value) {
    try {
      return Instant.parse(value);
    } catch (java.time.format.DateTimeParseException e) {
      // Some IdPs emit an explicit offset (…+00:00) instead of 'Z'; accept that too.
      return OffsetDateTime.parse(value).toInstant();
    }
  }

  private static byte[] decode(String base64Response) {
    if (base64Response == null || base64Response.isBlank()) {
      throw new SamlException("Missing SAMLResponse.");
    }
    try {
      // MIME decoder tolerates the line breaks an IdP may wrap the base64 in.
      return Base64.getMimeDecoder().decode(base64Response);
    } catch (IllegalArgumentException e) {
      throw new SamlException("SAMLResponse is not valid base64.", e);
    }
  }
}

package com.auvex.gateway.saml;

import com.auvex.gateway.egress.Pem;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignatureException;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Test-only SAML toolkit: one self-signed RSA key/cert, and a builder that produces genuinely
 * XML-DSig-signed SAML responses (plus deliberately broken ones) so the validator and ACS can be
 * proven against real cryptography rather than mocks.
 *
 * <p>The same key signs assertions here and its certificate is what the tests configure as the IdP
 * signing cert, so a happy-path response verifies and any tampering is caught.
 */
public final class SamlTestCrypto {

  public static final String ENTITY_ID = "https://idp.example/entity";
  public static final String SP_ENTITY_ID = "https://auvex.example/sp";
  public static final String ACS_URL = "https://auvex.example/auth/saml/okta/acs";

  // Declared in dependency order: the key signs the cert, and the cert is PEM-encoded.
  static final KeyPair KEY_PAIR = newRsaKeyPair();
  static final X509Certificate CERTIFICATE = selfSignedCertificate();
  public static final String CERT_PEM = encodePem();

  private SamlTestCrypto() {}

  /**
   * A builder for a single SAML response; tweak fields, then call {@link Builder#buildBase64()}.
   */
  public static Builder response() {
    return new Builder();
  }

  public static final class Builder {
    private String email = "user@corp.example";
    private String name = "Test User";
    private String issuer = ENTITY_ID;
    private String audience = SP_ENTITY_ID;
    private String recipient = ACS_URL;
    private String inResponseTo = "";
    private Instant notBefore = Instant.now().minus(Duration.ofMinutes(5));
    private Instant notOnOrAfter = Instant.now().plus(Duration.ofMinutes(5));
    private boolean sign = true;
    private boolean tamperAfterSigning = false;
    private boolean addWrappingAssertion = false;
    private String wrappingEmail = "attacker@evil.example";

    public Builder email(String value) {
      this.email = value;
      return this;
    }

    public Builder audience(String value) {
      this.audience = value;
      return this;
    }

    public Builder inResponseTo(String value) {
      this.inResponseTo = value;
      return this;
    }

    public Builder expired() {
      this.notBefore = Instant.now().minus(Duration.ofHours(2));
      this.notOnOrAfter = Instant.now().minus(Duration.ofHours(1));
      return this;
    }

    public Builder unsigned() {
      this.sign = false;
      return this;
    }

    public Builder tampered() {
      this.tamperAfterSigning = true;
      return this;
    }

    public Builder withWrappingAssertion() {
      this.addWrappingAssertion = true;
      return this;
    }

    public String buildBase64() {
      String genuineId = "_assertion_genuine";
      String xml = document(genuineId);
      Document doc = SamlXml.parse(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      if (sign) {
        signAssertion(doc, genuineId);
      }
      if (tamperAfterSigning) {
        tamper(doc, genuineId);
      }
      return Base64.getEncoder().encodeToString(serialize(doc));
    }

    // A single-line document (no inter-element whitespace) so signing round-trips byte-for-byte.
    private String document(String genuineId) {
      String wrapping = addWrappingAssertion ? assertion("_assertion_forged", wrappingEmail) : "";
      return "<samlp:Response xmlns:samlp=\""
          + SamlXml.PROTOCOL_NS
          + "\""
          + " xmlns:saml=\""
          + SamlXml.ASSERTION_NS
          + "\""
          + " ID=\"_response\" Version=\"2.0\" IssueInstant=\""
          + Instant.now()
          + "\">"
          + "<saml:Issuer>"
          + issuer
          + "</saml:Issuer>"
          + "<samlp:Status><samlp:StatusCode"
          + " Value=\"urn:oasis:names:tc:SAML:2.0:status:Success\"/></samlp:Status>"
          + wrapping
          + assertion(genuineId, email)
          + "</samlp:Response>";
    }

    private String assertion(String id, String userEmail) {
      String inResponseToAttr =
          inResponseTo.isBlank() ? "" : " InResponseTo=\"" + inResponseTo + "\"";
      return "<saml:Assertion xmlns:saml=\""
          + SamlXml.ASSERTION_NS
          + "\""
          + " ID=\""
          + id
          + "\" Version=\"2.0\" IssueInstant=\""
          + Instant.now()
          + "\">"
          + "<saml:Issuer>"
          + issuer
          + "</saml:Issuer>"
          + "<saml:Subject>"
          + "<saml:NameID Format=\"urn:oasis:names:tc:SAML:2.0:nameid-format:emailAddress\">"
          + userEmail
          + "</saml:NameID>"
          + "<saml:SubjectConfirmation Method=\"urn:oasis:names:tc:SAML:2.0:cm:bearer\">"
          + "<saml:SubjectConfirmationData NotOnOrAfter=\""
          + notOnOrAfter
          + "\""
          + " Recipient=\""
          + recipient
          + "\""
          + inResponseToAttr
          + "/>"
          + "</saml:SubjectConfirmation>"
          + "</saml:Subject>"
          + "<saml:Conditions NotBefore=\""
          + notBefore
          + "\" NotOnOrAfter=\""
          + notOnOrAfter
          + "\">"
          + "<saml:AudienceRestriction><saml:Audience>"
          + audience
          + "</saml:Audience>"
          + "</saml:AudienceRestriction>"
          + "</saml:Conditions>"
          + "<saml:AttributeStatement>"
          + "<saml:Attribute Name=\"email\"><saml:AttributeValue>"
          + userEmail
          + "</saml:AttributeValue></saml:Attribute>"
          + "<saml:Attribute Name=\"displayName\"><saml:AttributeValue>"
          + name
          + "</saml:AttributeValue></saml:Attribute>"
          + "</saml:AttributeStatement>"
          + "</saml:Assertion>";
    }

    private void signAssertion(Document doc, String assertionId) {
      Element assertion = doc.getElementById(assertionId);
      Element subject = SamlXml.directChild(assertion, "Subject");
      try {
        XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");
        Reference reference =
            factory.newReference(
                "#" + assertionId,
                factory.newDigestMethod(DigestMethod.SHA256, null),
                List.of(
                    factory.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null),
                    factory.newTransform(
                        CanonicalizationMethod.EXCLUSIVE, (TransformParameterSpec) null)),
                null,
                null);
        SignedInfo signedInfo =
            factory.newSignedInfo(
                factory.newCanonicalizationMethod(
                    CanonicalizationMethod.EXCLUSIVE, (C14NMethodParameterSpec) null),
                factory.newSignatureMethod(
                    "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256", null),
                List.of(reference));
        KeyInfoFactory keyInfoFactory = factory.getKeyInfoFactory();
        X509Data x509Data = keyInfoFactory.newX509Data(List.of(CERTIFICATE));
        KeyInfo keyInfo = keyInfoFactory.newKeyInfo(List.of(x509Data));

        DOMSignContext context = new DOMSignContext(KEY_PAIR.getPrivate(), assertion);
        // Place the signature right after Issuer (where the SAML schema expects it).
        context.setNextSibling(subject);
        factory.newXMLSignature(signedInfo, keyInfo).sign(context);
      } catch (java.security.GeneralSecurityException
          | javax.xml.crypto.MarshalException
          | XMLSignatureException e) {
        throw new IllegalStateException("Test failed to sign the SAML assertion", e);
      }
    }

    // Mutate the signed assertion after the fact, so its digest no longer matches the signature.
    private void tamper(Document doc, String assertionId) {
      Element assertion = doc.getElementById(assertionId);
      Element subject = SamlXml.directChild(assertion, "Subject");
      Element nameId = SamlXml.directChild(subject, "NameID");
      nameId.setTextContent("tampered@evil.example");
    }
  }

  private static byte[] serialize(Document doc) {
    try {
      TransformerFactory factory = TransformerFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
      Transformer transformer = factory.newTransformer();
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      transformer.transform(new DOMSource(doc), new StreamResult(out));
      return out.toByteArray();
    } catch (javax.xml.transform.TransformerException e) {
      throw new IllegalStateException("Test failed to serialize the SAML document", e);
    }
  }

  private static KeyPair newRsaKeyPair() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048, new SecureRandom());
      return generator.generateKeyPair();
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException("Test failed to generate an RSA key pair", e);
    }
  }

  private static X509Certificate selfSignedCertificate() {
    try {
      X500Name subject = new X500Name("CN=Auvex SAML Test IdP");
      Instant now = Instant.now();
      JcaX509v3CertificateBuilder builder =
          new JcaX509v3CertificateBuilder(
              subject,
              new BigInteger(64, new SecureRandom()),
              Date.from(now.minus(Duration.ofDays(1))),
              Date.from(now.plus(Duration.ofDays(3650))),
              subject,
              KEY_PAIR.getPublic());
      ContentSigner signer =
          new JcaContentSignerBuilder("SHA256withRSA").build(KEY_PAIR.getPrivate());
      return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    } catch (org.bouncycastle.operator.OperatorCreationException
        | java.security.cert.CertificateException e) {
      throw new IllegalStateException("Test failed to build a self-signed certificate", e);
    }
  }

  private static String encodePem() {
    try {
      return Pem.encode(CERTIFICATE);
    } catch (java.security.cert.CertificateEncodingException e) {
      throw new IllegalStateException("Test failed to PEM-encode the certificate", e);
    }
  }
}

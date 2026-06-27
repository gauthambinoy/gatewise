package com.auvex.gateway.saml;

import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * XML parsing for SAML, locked down against XML attacks.
 *
 * <p>SAML responses are attacker-supplied XML, so the parser is hardened: DTDs are forbidden
 * outright and every external-entity / external-DTD avenue is switched off, which closes XXE and
 * billion-laughs entity-expansion. Parsing also registers the SAML {@code ID} attributes so
 * same-document signature references ({@code #_assertionId}) resolve, and rejects a document that
 * reuses an ID — a duplicate ID is a classic lever for signature-wrapping, where one element is
 * signed and a same-id twin is read.
 */
final class SamlXml {

  static final String ASSERTION_NS = "urn:oasis:names:tc:SAML:2.0:assertion";
  static final String PROTOCOL_NS = "urn:oasis:names:tc:SAML:2.0:protocol";

  private SamlXml() {}

  /** Parses hardened, namespace-aware XML and registers the SAML {@code ID} attributes. */
  static Document parse(byte[] xml) {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    try {
      factory.setNamespaceAware(true);
      // No DTDs at all — the single most effective XXE/entity-expansion defence.
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document document = builder.parse(new ByteArrayInputStream(xml));
      Set<String> seen = new HashSet<>();
      registerIds(document.getDocumentElement(), seen);
      return document;
    } catch (ParserConfigurationException | org.xml.sax.SAXException | java.io.IOException e) {
      throw new SamlException("SAML response is not well-formed XML.", e);
    }
  }

  // Walk the tree marking every unqualified "ID" attribute as a DOM id (so getElementById and the
  // signature's same-document reference both resolve), and fail on any duplicate id.
  private static void registerIds(Element element, Set<String> seen) {
    if (element.hasAttribute("ID")) {
      String id = element.getAttribute("ID");
      if (!seen.add(id)) {
        throw new SamlException("SAML response reuses element ID '" + id + "' (wrapping attempt).");
      }
      element.setIdAttribute("ID", true);
    }
    NodeList children = element.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (child instanceof Element childElement) {
        registerIds(childElement, seen);
      }
    }
  }

  /** First direct child element of {@code parent} in the SAML assertion namespace, or null. */
  static Element directChild(Element parent, String localName) {
    NodeList children = parent.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (child instanceof Element element
          && ASSERTION_NS.equals(element.getNamespaceURI())
          && localName.equals(element.getLocalName())) {
        return element;
      }
    }
    return null;
  }

  /** True when {@code candidate} is {@code ancestor} itself or sits somewhere beneath it. */
  static boolean contains(Element ancestor, Node candidate) {
    for (Node node = candidate; node != null; node = node.getParentNode()) {
      if (node == ancestor) {
        return true;
      }
    }
    return false;
  }
}

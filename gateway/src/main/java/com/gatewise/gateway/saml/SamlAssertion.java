package com.gatewise.gateway.saml;

/**
 * The verified identity carried by a SAML assertion, after every signature and condition check has
 * passed.
 *
 * @param subject the assertion {@code NameID} (the IdP's stable handle for the user)
 * @param email the user's email (from a mail attribute, or the NameID if it is an email)
 * @param name the user's display name, or empty if the IdP didn't send one
 */
public record SamlAssertion(String subject, String email, String name) {}

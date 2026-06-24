package com.auvex.gateway.oidc;

/** The verified identity from an OIDC id_token. */
public record OidcUser(String subject, String email, boolean emailVerified, String name) {}

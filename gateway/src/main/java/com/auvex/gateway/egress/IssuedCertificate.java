package com.auvex.gateway.egress;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/**
 * A leaf certificate issued by the egress CA, with its private key, for serving an intercepted TLS
 * connection.
 */
public record IssuedCertificate(X509Certificate certificate, PrivateKey privateKey) {}

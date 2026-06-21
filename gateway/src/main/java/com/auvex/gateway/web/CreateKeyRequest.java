package com.auvex.gateway.web;

/** The body for creating an API key. The name is optional (defaults to a generic label). */
public record CreateKeyRequest(String name) {}

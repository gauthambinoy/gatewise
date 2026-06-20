package com.auvex.gateway.web;

import java.util.UUID;

/** Public projection of a tenant returned by the API. */
public record TenantView(UUID id, String name, String slug) {}

package com.gatewise.gateway.routing;

import jakarta.validation.constraints.NotEmpty;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Maps client-facing model aliases to the real provider models they route to.
 *
 * <p>The map doubles as the allow-list: only the aliases listed here can be used, so an operator
 * controls exactly which models a tenant may reach. It must be non-empty, or the gateway would
 * refuse every request — so we fail startup rather than boot into that state.
 *
 * @param models alias → provider model (e.g. {@code fast → openai/gpt-4o-mini})
 */
@Validated
@ConfigurationProperties(prefix = "gatewise.routing")
public record ModelRoutingProperties(@NotEmpty Map<String, String> models) {}

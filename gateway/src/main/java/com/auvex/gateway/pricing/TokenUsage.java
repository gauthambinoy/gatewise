package com.auvex.gateway.pricing;

/** Token usage extracted from a provider response. */
public record TokenUsage(int promptTokens, int completionTokens) {}

package com.auvex.gateway.redaction;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Detects Stripe secret/publishable/restricted keys (e.g. {@code sk_live_...}). */
@Component
public class StripeKeyDetector extends RegexDetector {

  private static final Pattern STRIPE_KEY =
      Pattern.compile("\\b(?:sk|pk|rk)_(?:live|test)_[A-Za-z0-9]{16,}\\b");

  public StripeKeyDetector() {
    super(PiiType.STRIPE_KEY, 9, STRIPE_KEY);
  }
}

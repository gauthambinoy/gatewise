package com.auvex.gateway.redaction;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Detects IEEE 802 MAC addresses in colon-separated hex form (e.g. {@code 00:1A:2B:3C:4D:5E}). */
@Component
public class MacAddressDetector extends RegexDetector {

  private static final Pattern MAC_ADDRESS =
      Pattern.compile("\\b(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}\\b");

  public MacAddressDetector() {
    super(PiiType.MAC_ADDRESS, 6, MAC_ADDRESS);
  }
}

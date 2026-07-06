package com.gatewise.gateway.redaction;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Detects IPv4 addresses, validating that every octet is in the 0-255 range. */
@Component
public class IpAddressDetector extends RegexDetector {

  private static final Pattern IPV4 = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");

  public IpAddressDetector() {
    super(PiiType.IP_ADDRESS, 6, IPV4);
  }

  @Override
  protected boolean accept(Matcher matcher) {
    for (String octet : matcher.group().split("\\.")) {
      if (Integer.parseInt(octet) > 255) {
        return false;
      }
    }
    return true;
  }
}

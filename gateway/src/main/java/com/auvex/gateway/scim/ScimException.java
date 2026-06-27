package com.auvex.gateway.scim;

/**
 * A SCIM request error, carrying the HTTP status and an optional SCIM {@code scimType} so the
 * handler can render the SCIM 2.0 error envelope (e.g. {@code uniqueness} for a duplicate
 * userName).
 */
public class ScimException extends RuntimeException {

  private final int status;
  private final String scimType;

  public ScimException(int status, String detail) {
    this(status, detail, null);
  }

  public ScimException(int status, String detail, String scimType) {
    super(detail);
    this.status = status;
    this.scimType = scimType;
  }

  public int status() {
    return status;
  }

  public String scimType() {
    return scimType;
  }
}

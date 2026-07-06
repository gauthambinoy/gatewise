package com.gatewise.gateway.proxy;

/**
 * A fully-buffered upstream response — status, content type and body — suitable for caching and for
 * writing back to the caller. Used on the non-streaming path.
 */
public record CachedResponse(int status, String contentType, String body) {

  /** True for a 2xx response (the only kind worth caching). */
  public boolean isSuccessful() {
    return status >= 200 && status < 300;
  }
}

package com.example.router;

import java.util.Locale;

/**
 * HTTP methods accepted by the router. Method names are handled
 * case-sensitively in their canonical upper-case form; {@link #from(String)}
 * accepts the standard upper- or lower-case spellings (e.g. {@code get}).
 */
public enum HttpMethod {
  GET,
  POST,
  PUT,
  DELETE,
  PATCH,
  HEAD,
  OPTIONS;

  public static HttpMethod from(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("HTTP method must not be null or blank");
    }
    String normalized = raw.trim();
    if (!normalized.toUpperCase(Locale.ROOT).equals(normalized)
        && !normalized.toLowerCase(Locale.ROOT).equals(normalized)) {
      throw new IllegalArgumentException("Unsupported HTTP method: " + raw);
    }
    try {
      return valueOf(normalized.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Unsupported HTTP method: " + raw, e);
    }
  }
}

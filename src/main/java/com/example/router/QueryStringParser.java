package com.example.router;

import com.example.router.exception.ParamBindingException;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal {@code application/x-www-form-urlencoded}-style query parser.
 *
 * <p>Rules: parameters are split on {@code &}; a parameter is either
 * {@code key=value} or a bare {@code key} (value defaults to empty string);
 * keys and values are percent-decoded as UTF-8, and {@code +} decodes to a
 * space. Repeated keys keep the last occurrence. Malformed escapes raise a
 * binding error rather than being silently ignored.
 */
final class QueryStringParser {

  private QueryStringParser() {
  }

  static Map<String, String> parse(String queryString) {
    Map<String, String> params = new LinkedHashMap<>();
    if (queryString == null || queryString.isEmpty()) {
      return params;
    }
    for (String pair : queryString.split("&", -1)) {
      if (pair.isEmpty()) {
        throw new ParamBindingException("Malformed query string: empty parameter entry");
      }
      int eq = pair.indexOf('=');
      String rawKey = eq < 0 ? pair : pair.substring(0, eq);
      String rawValue = eq < 0 ? "" : pair.substring(eq + 1);
      if (rawKey.isEmpty()) {
        throw new ParamBindingException("Malformed query string: parameter without a name");
      }
      params.put(decode(rawKey, true), decode(rawValue, true));
    }
    return params;
  }

  private static String decode(String value, boolean plusAsSpace) {
    try {
      return URLDecoder.decode(value, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      throw new ParamBindingException("Malformed percent-encoding in query: '" + value + "'", e);
    }
  }
}

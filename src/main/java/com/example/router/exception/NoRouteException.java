package com.example.router.exception;

import java.util.Set;

/**
 * Raised when no route matches the request path (maps to HTTP 404).
 * If other routes match the same path for different HTTP methods, the
 * allowed methods are exposed so callers can instead answer 405 with an
 * Allow header.
 */
public class NoRouteException extends RouterException {

  private final Set<String> allowedMethods;

  public NoRouteException(String message, Set<String> allowedMethods) {
    super(message);
    this.allowedMethods = Set.copyOf(allowedMethods);
  }

  /**
   * @return HTTP methods that are registered on the matched path; empty
   *     when the path itself is unknown (true 404).
   */
  public Set<String> allowedMethods() {
    return allowedMethods;
  }
}

package com.example.router.exception;

/**
 * Raised immediately while registering a route when the route table would
 * become invalid: duplicate method+path registration, structurally
 * ambiguous patterns, malformed patterns, or unsupported handler signatures.
 */
public class RouteRegistrationException extends RouterException {

  public RouteRegistrationException(String message) {
    super(message);
  }

  public RouteRegistrationException(String message, Throwable cause) {
    super(message, cause);
  }
}

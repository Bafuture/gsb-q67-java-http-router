package com.example.router.exception;

/**
 * Raised when reflection invocation of a matched handler fails.
 * The handler's own thrown cause is preserved as {@link #getCause()}.
 */
public class RouteInvocationException extends RouterException {

  public RouteInvocationException(String message, Throwable cause) {
    super(message, cause);
  }
}

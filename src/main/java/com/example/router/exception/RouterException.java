package com.example.router.exception;

/**
 * Base class for all errors raised by the router, both at registration time
 * (programmer mistakes) and at dispatch time (bad client input or no route).
 */
public class RouterException extends RuntimeException {

  public RouterException(String message) {
    super(message);
  }

  public RouterException(String message, Throwable cause) {
    super(message, cause);
  }
}

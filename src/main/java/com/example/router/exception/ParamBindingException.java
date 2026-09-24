package com.example.router.exception;

/**
 * Raised when a request parameter is missing or its raw value cannot be
 * converted to the declared Java type. The message names the parameter,
 * its location and the target type so callers can map it to a 400 response.
 */
public class ParamBindingException extends RouterException {

  public ParamBindingException(String message) {
    super(message);
  }

  public ParamBindingException(String message, Throwable cause) {
    super(message, cause);
  }
}

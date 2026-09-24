package com.example.gsb.router;

/** Thrown when a path or query parameter cannot be bound to a handler method parameter. */
public class ParameterBindingException extends RuntimeException {
    public ParameterBindingException(String message) {
        super(message);
    }

    public ParameterBindingException(String message, Throwable cause) {
        super(message, cause);
    }
}

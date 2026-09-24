package com.example.gsb.router;

/** Thrown when a route is registered that conflicts with an existing route for the same HTTP method. */
public class DuplicateRouteException extends RuntimeException {
    public DuplicateRouteException(String message) {
        super(message);
    }
}

package com.example.gsb.router;

/** Supported HTTP methods. Parsing is case-insensitive. */
public enum HttpMethod {
    GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS;

    public static HttpMethod of(String name) {
        for (HttpMethod method : values()) {
            if (method.name().equalsIgnoreCase(name)) {
                return method;
            }
        }
        throw new IllegalArgumentException("Unsupported HTTP method: " + name);
    }
}

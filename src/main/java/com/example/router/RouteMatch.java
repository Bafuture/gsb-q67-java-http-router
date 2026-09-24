package com.example.router;

import java.util.Map;

/**
 * Result of matching a request: the winning {@link Route} plus the captured
 * path/wildcard variables (already percent-decoded).
 */
public record RouteMatch(Route route, Map<String, String> pathVariables) {
}

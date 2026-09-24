package com.example.gsb.router;

import java.util.Map;

/** A route together with the path variables extracted from a concrete request path. */
public record RouteMatch(Route route, Map<String, String> pathVariables) {
}

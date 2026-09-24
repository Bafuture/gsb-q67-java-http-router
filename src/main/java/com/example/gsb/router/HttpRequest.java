package com.example.gsb.router;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A minimal HTTP request abstraction for the router. The raw target may include a
 * query string ({@code /users/42?verbose=true}); it is split into path and query
 * parameters. Repeated query keys keep the first value.
 */
public record HttpRequest(HttpMethod method, String path, Map<String, String> queryParams) {

    public HttpRequest {
        queryParams = Collections.unmodifiableMap(new LinkedHashMap<>(queryParams));
    }

    public static HttpRequest of(String method, String target) {
        return of(HttpMethod.of(method), target);
    }

    public static HttpRequest of(HttpMethod method, String target) {
        String path = target;
        Map<String, String> query = new LinkedHashMap<>();
        int q = target.indexOf('?');
        if (q >= 0) {
            path = target.substring(0, q);
            String queryString = target.substring(q + 1);
            for (String pair : queryString.split("&")) {
                if (pair.isEmpty()) {
                    continue;
                }
                int eq = pair.indexOf('=');
                String key = eq >= 0 ? pair.substring(0, eq) : pair;
                String value = eq >= 0 ? pair.substring(eq + 1) : "";
                query.putIfAbsent(decode(key), decode(value));
            }
        }
        if (path.isEmpty()) {
            path = "/";
        }
        return new HttpRequest(method, path, query);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}

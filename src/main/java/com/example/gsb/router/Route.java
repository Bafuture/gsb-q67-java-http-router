package com.example.gsb.router;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * A registered route: HTTP method + path pattern + handler method on a target object.
 */
public final class Route implements Comparable<Route> {

    private final HttpMethod method;
    private final PathPattern pattern;
    private final Object target;
    private final Method handler;

    Route(HttpMethod method, PathPattern pattern, Object target, Method handler) {
        this.method = method;
        this.pattern = pattern;
        this.target = target;
        this.handler = handler;
        this.handler.setAccessible(true);
    }

    public HttpMethod method() {
        return method;
    }

    public PathPattern pattern() {
        return pattern;
    }

    public Method handler() {
        return handler;
    }

    public String handlerName() {
        return target.getClass().getSimpleName() + "#" + handler.getName();
    }

    Map<String, String> match(String path) {
        return pattern.match(path);
    }

    Object invoke(Object... args) throws Exception {
        return handler.invoke(target, args);
    }

    /** Routes sort by pattern priority first, then by HTTP method name for a stable table. */
    @Override
    public int compareTo(Route other) {
        int byPattern = pattern.compareTo(other.pattern);
        return byPattern != 0 ? byPattern : method.compareTo(other.method);
    }

    @Override
    public String toString() {
        return method + " " + pattern;
    }
}

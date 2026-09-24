package com.example.gsb.router;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The HTTP router. Routes are registered with {@link #route(String, String, Object, String)}
 * and requests are dispatched with {@link #handle(HttpRequest)}.
 *
 * <p>Registration fails fast: a route whose pattern structurally conflicts with an
 * already registered pattern for the same HTTP method raises
 * {@link DuplicateRouteException} immediately.
 *
 * <p>Matching iterates routes in priority order (see {@link PathPattern}); the first
 * route matching the request path wins.
 */
public final class Router {

    private final List<Route> routes = new ArrayList<>();

    /**
     * Registers a handler method. The method is looked up on the target's class by name;
     * it must be unique (no overloads).
     *
     * @throws DuplicateRouteException if the same method+path shape is already registered
     * @throws IllegalArgumentException if the pattern, method or parameters are invalid
     */
    public Router route(String httpMethod, String pattern, Object target, String handlerName) {
        return route(HttpMethod.of(httpMethod), pattern, target, findHandler(target.getClass(), handlerName));
    }

    public Router route(HttpMethod httpMethod, String pattern, Object target, Method handler) {
        PathPattern pathPattern = PathPattern.parse(pattern);
        ParameterBinder.validate(handler, pathPattern);
        for (Route existing : routes) {
            if (existing.method() == httpMethod && existing.pattern().conflictsWith(pathPattern)) {
                throw new DuplicateRouteException("Route " + httpMethod + " " + pattern
                        + " conflicts with already registered " + existing.method()
                        + " " + existing.pattern());
            }
        }
        routes.add(new Route(httpMethod, pathPattern, target, handler));
        return this;
    }

    private static Method findHandler(Class<?> type, String name) {
        Method found = null;
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                if (found != null) {
                    throw new IllegalArgumentException(
                            "Ambiguous handler name '" + name + "' on " + type.getName() + ": overloads are not supported");
                }
                found = method;
            }
        }
        if (found == null) {
            throw new IllegalArgumentException("No method '" + name + "' on " + type.getName());
        }
        return found;
    }

    /** All registered routes in matching priority order. */
    public List<Route> routes() {
        List<Route> sorted = new ArrayList<>(routes);
        Collections.sort(sorted);
        return sorted;
    }

    /**
     * Finds the route that would serve the given method and path.
     *
     * @return the route and its extracted path variables, or empty when nothing matches
     */
    public Optional<RouteMatch> match(HttpMethod method, String path) {
        for (Route route : routes()) {
            if (route.method() != method) {
                continue;
            }
            Map<String, String> vars = route.match(path);
            if (vars != null) {
                return Optional.of(new RouteMatch(route, vars));
            }
        }
        return Optional.empty();
    }

    /** Dispatches a request and always produces a response (never throws for user errors). */
    public HttpResponse handle(HttpRequest request) {
        Optional<RouteMatch> match = match(request.method(), request.path());
        if (match.isEmpty()) {
            if (pathExistsForOtherMethod(request)) {
                return HttpResponse.error(405, "Method " + request.method()
                        + " not allowed for path " + request.path());
            }
            return HttpResponse.error(404, "No route for " + request.method() + " " + request.path());
        }
        RouteMatch routeMatch = match.get();
        Route route = routeMatch.route();
        Object[] args;
        try {
            args = ParameterBinder.bind(route.handler(), routeMatch.pathVariables(), request.queryParams());
        } catch (ParameterBindingException e) {
            return HttpResponse.error(400, e.getMessage());
        }
        try {
            Object result = route.invoke(args);
            return route.handler().getReturnType() == void.class
                    ? HttpResponse.noContent()
                    : HttpResponse.ok(result);
        } catch (InvocationTargetException e) {
            return HttpResponse.error(500, "Handler threw: " + e.getCause());
        } catch (Exception e) {
            return HttpResponse.error(500, "Failed to invoke handler: " + e);
        }
    }

    public HttpResponse handle(String method, String target) {
        return handle(HttpRequest.of(method, target));
    }

    private boolean pathExistsForOtherMethod(HttpRequest request) {
        return routes.stream()
                .filter(route -> route.method() != request.method())
                .anyMatch(route -> route.match(request.path()) != null);
    }

    /** Renders all routes as a text table ordered by matching priority. */
    public String table() {
        return RouteTable.render(routes());
    }
}

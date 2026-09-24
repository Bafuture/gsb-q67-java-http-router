package com.example.router;

import com.example.router.exception.NoRouteException;
import com.example.router.exception.RouteInvocationException;
import com.example.router.exception.RouteRegistrationException;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Small in-memory HTTP request router: registers handler methods bound to
 * path patterns, resolves a request (method + raw path) to the highest
 * priority match, converts its parameters and invokes the handler.
 *
 * <h2>Matching rules</h2>
 * <ul>
 *   <li>Paths are split on {@code /}. A pattern segment is either a literal,
 *       a {@code {param}} or a trailing {@code **} (zero or more segments).</li>
 *   <li>Priority, highest first: patterns without a wildcard; patterns with
 *       more fixed segments; literal segments before parameter segments
 *       (compared left to right). Effectively static &gt; parameter &gt; wildcard.</li>
 *   <li>Matching is case-sensitive for both literals and enum values.</li>
 *   <li>Trailing slashes are not normalized: {@code /users/} is rejected with
 *       {@link IllegalArgumentException} for every route except root
 *       {@code /}. Clients must use the canonical form.</li>
 *   <li>Registering the same HTTP method on the same path pattern twice fails
 *       immediately, as do structurally identical patterns (same shape but
 *       different parameter names) that the matcher could not distinguish.</li>
 * </ul>
 */
public final class Router {

  private final List<Route> routes = new ArrayList<>();

  /**
   * Registers a handler method.
   *
   * @param method HTTP method (canonical upper-case enum value)
   * @param pathPattern raw path, e.g. {@code /users/{id}} or {@code /files/**}
   * @param target object the handler is invoked on
   * @param handlerName name of the handler method (resolved by reflection);
   *     when overloaded, the unique method is chosen via {@link #register}
   * @return this router for fluent chaining
   */
  public Router register(HttpMethod method, String pathPattern,
                         Object target, String handlerName) {
    Method handler = resolveMethod(target, handlerName, pathPattern);
    return register(method, pathPattern, target, handler);
  }

  /**
   * Registers an explicit {@link Method} reference, useful for overloaded
   * handlers.
   */
  public Router register(HttpMethod method, String pathPattern,
                         Object target, Method handler) {
    if (method == null) {
      throw new RouteRegistrationException("HTTP method must not be null");
    }
    if (target == null) {
      throw new RouteRegistrationException("Handler target must not be null");
    }
    if (handler == null) {
      throw new RouteRegistrationException("Handler method must not be null");
    }
    if (!handler.getDeclaringClass().isInstance(target)) {
      throw new RouteRegistrationException(
          "Target " + target.getClass().getSimpleName()
              + " is not an instance of the handler's declaring class "
              + handler.getDeclaringClass().getSimpleName());
    }
    RoutePattern pattern = RoutePattern.parse(pathPattern);
    for (Route existing : routes) {
      if (existing.method() == method && existing.pattern().raw().equals(pattern.raw())) {
        throw new RouteRegistrationException(
            "Route " + method + " " + pattern.raw()
                + " is already registered (duplicate handler: "
                + existing.handlerLabel() + ")");
      }
      if (existing.method() == method
          && existing.pattern().structurallyEquals(pattern)) {
        throw new RouteRegistrationException(
            "Route " + method + " " + pattern.raw()
                + " is structurally identical to existing route "
                + existing.pattern().raw()
                + "; parameter names differ but match priority is equal");
      }
    }
    ParameterBinder.validate(handler, pattern);
    routes.add(new Route(method, pattern, target, handler));
    return this;
  }

  /**
   * Finds the winning route for a method and raw request path without
   * invoking it. The path must not carry a query string and must use the
   * canonical (no trailing slash) form.
   */
  public Optional<RouteMatch> route(HttpMethod method, String rawPath) {
    List<String> segments = splitRequestPath(rawPath);
    RouteMatch winner = null;
    for (Route route : routes) {
      Map<String, String> captured = route.pattern().match(segments);
      if (captured == null) {
        continue;
      }
      if (winner == null
          || route.pattern().compareTo(winner.route().pattern()) < 0) {
        winner = new RouteMatch(route, Map.copyOf(captured));
      }
    }
    if (winner == null || winner.route().method() != method) {
      return Optional.empty();
    }
    return Optional.of(winner);
  }

  /**
   * Resolves, binds and invokes the handler for a request line such as
   * {@code GET /users/42?detail=full}.
   *
   * @throws NoRouteException when nothing matches (with allowed-method info)
   * @throws com.example.router.exception.ParamBindingException on bad parameters
   * @throws RouteInvocationException when the handler itself throws
   */
  public Object dispatch(String requestLine) {
    if (requestLine == null || requestLine.isBlank()) {
      throw new IllegalArgumentException("Request line must not be null or blank");
    }
    String trimmed = requestLine.trim();
    int space = trimmed.indexOf(' ');
    if (space <= 0) {
      throw new IllegalArgumentException(
          "Request line must be 'METHOD /path[?query]': " + requestLine);
    }
    HttpMethod method = HttpMethod.from(trimmed.substring(0, space));
    String target = trimmed.substring(space + 1);
    if (target.contains(" ")) {
      throw new IllegalArgumentException("Request target must not contain spaces: " + target);
    }
    int question = target.indexOf('?');
    String rawPath = question < 0 ? target : target.substring(0, question);
    String rawQuery = question < 0 ? "" : target.substring(question + 1);
    return dispatch(method, rawPath, rawQuery);
  }

  /**
   * Resolves, binds and invokes the handler for a method, path and query
   * string.
   */
  public Object dispatch(HttpMethod method, String rawPath, String queryString) {
    if (method == null) {
      throw new IllegalArgumentException("HTTP method must not be null");
    }
    List<String> segments = splitRequestPath(rawPath);
    Map<String, String> queryParameters = QueryStringParser.parse(queryString);

    RouteMatch best = null;
    Set<String> allowedMethods = new TreeSet<>();
    for (Route route : routes) {
      Map<String, String> captured = route.pattern().match(segments);
      if (captured == null) {
        continue;
      }
      allowedMethods.add(route.method().name());
      if (route.method() != method) {
        continue;
      }
      if (best == null
          || route.pattern().compareTo(best.route().pattern()) < 0) {
        best = new RouteMatch(route, Map.copyOf(captured));
      }
    }
    if (best == null) {
      if (allowedMethods.isEmpty()) {
        throw new NoRouteException("No route matches " + method + " " + rawPath, Set.of());
      }
      throw new NoRouteException(
          "Path " + rawPath + " does not support method " + method
              + "; allowed: " + allowedMethods,
          allowedMethods);
    }

    Route route = best.route();
    Object[] arguments;
    try {
      arguments = ParameterBinder.bind(
          route.handler(), best.pathVariables(), queryParameters);
    } catch (com.example.router.exception.ParamBindingException e) {
      throw e;
    }
    try {
      Method handler = route.handler();
      handler.setAccessible(true);
      return handler.invoke(route.target(), arguments);
    } catch (java.lang.reflect.InvocationTargetException e) {
      throw new RouteInvocationException(
          "Handler " + route.handlerLabel() + " threw an exception",
          e.getCause() != null ? e.getCause() : e);
    } catch (IllegalAccessException e) {
      throw new RouteInvocationException(
          "Cannot invoke handler " + route.handlerLabel(), e);
    }
  }

  /** All registered routes, ordered by routing priority (most specific first). */
  public List<Route> routes() {
    return routesSorted();
  }

  /**
   * Renders every registered route as a fixed-width text table, ordered by
   * priority. Columns: {@code PRIORITY}, {@code METHOD}, {@code PATH},
   * {@code HANDLER}.
   */
  public String renderRouteTable() {
    List<Route> sorted = routesSorted();
    String priorityHeader = "PRIORITY";
    String methodHeader = "METHOD";
    String pathHeader = "PATH";
    String handlerHeader = "HANDLER";

    int methodWidth = methodHeader.length();
    int pathWidth = pathHeader.length();
    int handlerWidth = handlerHeader.length();
    for (Route route : sorted) {
      methodWidth = Math.max(methodWidth, route.method().name().length());
      pathWidth = Math.max(pathWidth, route.pattern().raw().length());
      handlerWidth = Math.max(handlerWidth, route.handlerLabel().length());
    }
    String format = "%-" + priorityHeader.length() + "s  %-" + methodWidth + "s  %-"
        + pathWidth + "s  %-" + handlerWidth + "s%n";
    StringBuilder out = new StringBuilder();
    out.append(String.format(format.stripTrailing(),
        priorityHeader, methodHeader, pathHeader, handlerHeader));
    out.append("-".repeat(priorityHeader.length())).append("  ")
        .append("-".repeat(methodWidth)).append("  ")
        .append("-".repeat(pathWidth)).append("  ")
        .append("-".repeat(handlerWidth)).append(System.lineSeparator());
    for (int i = 0; i < sorted.size(); i++) {
      Route route = sorted.get(i);
      out.append(String.format(format.stripTrailing(),
          String.valueOf(i + 1),
          route.method().name(),
          route.pattern().raw(),
          route.handlerLabel()));
    }
    return out.toString().stripTrailing();
  }

  /** Number of registered routes. */
  public int size() {
    return routes.size();
  }

  private List<Route> routesSorted() {
    List<Route> sorted = new ArrayList<>(routes);
    sorted.sort(Comparator
        .comparing(Route::pattern)
        .thenComparing(route -> route.method().name()));
    return sorted;
  }

  private static List<String> splitRequestPath(String rawPath) {
    if (rawPath == null || rawPath.isEmpty()) {
      throw new IllegalArgumentException("Request path must not be null or empty");
    }
    if (!rawPath.startsWith("/")) {
      throw new IllegalArgumentException("Request path must start with '/': " + rawPath);
    }
    if (rawPath.length() > 1 && rawPath.endsWith("/")) {
      throw new IllegalArgumentException(
          "Request path must not end with '/' (trailing slashes are not normalized): "
              + rawPath);
    }
    if (rawPath.contains("//")) {
      throw new IllegalArgumentException(
          "Request path must not contain empty segments ('//'): " + rawPath);
    }
    List<String> segments = new ArrayList<>();
    if (rawPath.length() > 1) {
      for (String segment : rawPath.substring(1).split("/", -1)) {
        segments.add(segment);
      }
    }
    return segments;
  }

  private static Method resolveMethod(Object target, String handlerName,
                                      String pathPattern) {
    List<Method> candidates = new ArrayList<>();
    for (Method method : target.getClass().getMethods()) {
      if (method.getName().equals(handlerName)) {
        candidates.add(method);
      }
    }
    if (candidates.isEmpty()) {
      throw new RouteRegistrationException(
          "Handler method '" + handlerName + "' not found on "
              + target.getClass().getName() + " for pattern " + pathPattern);
    }
    if (candidates.size() > 1) {
      throw new RouteRegistrationException(
          "Handler method '" + handlerName + "' is overloaded on "
              + target.getClass().getSimpleName()
              + "; pass a Method reference via register(HttpMethod, String, Object, Method)");
    }
    return candidates.get(0);
  }
}

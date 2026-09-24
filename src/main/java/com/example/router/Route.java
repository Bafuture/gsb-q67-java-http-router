package com.example.router;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * A registered route: HTTP method + path pattern bound to a handler method
 * on a target object.
 */
public record Route(
    HttpMethod method,
    RoutePattern pattern,
    Object target,
    Method handler) {

  public Route {
    Objects.requireNonNull(method, "method");
    Objects.requireNonNull(pattern, "pattern");
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(handler, "handler");
  }

  /** Short, stable label used by the text route table. */
  public String handlerLabel() {
    return target.getClass().getSimpleName() + "#" + handler.getName();
  }
}

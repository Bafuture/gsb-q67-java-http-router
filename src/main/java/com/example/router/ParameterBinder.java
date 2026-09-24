package com.example.router;

import com.example.router.annotation.PathParam;
import com.example.router.annotation.QueryParam;
import com.example.router.annotation.WildcardParam;
import com.example.router.exception.ParamBindingException;
import com.example.router.exception.RouteRegistrationException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;

/**
 * Validates handler signatures at registration time and converts the raw
 * path/query/wildcard values of a request into the typed arguments of a
 * handler method.
 *
 * <p>Supported parameter types:
 * <ul>
 *   <li>{@link String};</li>
 *   <li>primitives and their wrappers except {@code char}/{@link Character};</li>
 *   <li>any {@link Enum} (matched case-sensitively by constant name);</li>
 *   <li>{@link LocalDate}, {@link LocalTime}, {@link LocalDateTime} parsed as
 *       ISO-8601 by default, or with a {@code pattern} on the annotation;
 *       {@link Instant} parsed as ISO-8601 (custom patterns are rejected).</li>
 * </ul>
 */
final class ParameterBinder {

  private static final Set<Class<?>> SUPPORTED_NUMERIC =
      Set.of(Byte.class, Short.class, Integer.class, Long.class,
          Float.class, Double.class);

  private ParameterBinder() {
  }

  /**
   * Verifies every parameter of a handler method carries exactly one binding
   * annotation, refers to an existing variable, and uses a supported type.
   * Called once per registration so failures surface immediately.
   */
  static void validate(Method handler, RoutePattern pattern) {
    Parameter[] parameters = handler.getParameters();
    boolean wildcardBound = false;
    for (Parameter parameter : parameters) {
      PathParam pathParam = parameter.getAnnotation(PathParam.class);
      QueryParam queryParam = parameter.getAnnotation(QueryParam.class);
      WildcardParam wildcardParam = parameter.getAnnotation(WildcardParam.class);
      int annotationCount = countPresent(pathParam, queryParam, wildcardParam);
      if (annotationCount == 0) {
        throw new RouteRegistrationException(
            "Handler parameter '" + parameter.getName() + "' of " + describe(handler)
                + " must carry one of @PathParam, @QueryParam or @WildcardParam");
      }
      if (annotationCount > 1) {
        throw new RouteRegistrationException(
            "Handler parameter '" + parameter.getName() + "' of " + describe(handler)
                + " carries multiple binding annotations; pick one");
      }
      Class<?> type = parameter.getType();
      if (pathParam != null) {
        if (RoutePattern.WILDCARD_VARIABLE.equals(pathParam.value())) {
          throw new RouteRegistrationException(
              "Path parameter name '*' is reserved for the wildcard remainder");
        }
        if (!pattern.parameterNames().contains(pathParam.value())) {
          throw new RouteRegistrationException(
              "@PathParam(\"" + pathParam.value() + "\") of " + describe(handler)
                  + " does not exist in pattern " + pattern.raw());
        }
        checkSupportedType(type, parameter, handler);
        checkPattern(pathParam.pattern(), type, parameter, handler);
      } else if (queryParam != null) {
        checkSupportedType(type, parameter, handler);
        checkPattern(queryParam.pattern(), type, parameter, handler);
        if (type.isPrimitive() && !queryParam.required()) {
          throw new RouteRegistrationException(
              "@QueryParam(\"" + queryParam.value() + "\") of " + describe(handler)
                  + " is optional but declared as primitive " + type.getSimpleName()
                  + "; use the wrapper type");
        }
      } else {
        if (wildcardBound) {
          throw new RouteRegistrationException(
              "Handler " + describe(handler) + " binds @WildcardParam more than once");
        }
        if (!pattern.isWildcard()) {
          throw new RouteRegistrationException(
              "@WildcardParam of " + describe(handler)
                  + " requires a '/**' wildcard pattern");
        }
        if (type != String.class) {
          throw new RouteRegistrationException(
              "@WildcardParam of " + describe(handler)
                  + " must be of type String but was " + type.getSimpleName());
        }
        wildcardBound = true;
      }
    }
  }

  /**
   * Builds the ordered argument array for invoking the matched handler.
   *
   * @throws ParamBindingException if a required value is missing or cannot
   *     be converted to the declared type
   */
  static Object[] bind(Method handler, Map<String, String> pathVariables,
                       Map<String, String> queryParameters) {
    Parameter[] parameters = handler.getParameters();
    Object[] args = new Object[parameters.length];
    for (int i = 0; i < parameters.length; i++) {
      Parameter parameter = parameters[i];
      PathParam pathParam = parameter.getAnnotation(PathParam.class);
      QueryParam queryParam = parameter.getAnnotation(QueryParam.class);
      WildcardParam wildcardParam = parameter.getAnnotation(WildcardParam.class);
      if (pathParam != null) {
        String raw = pathVariables.get(pathParam.value());
        if (raw == null) {
          throw new ParamBindingException(
              "Path parameter '" + pathParam.value() + "' was not captured by the route");
        }
        args[i] = convert(raw, parameter.getType(), pathParam.pattern(),
            "path", pathParam.value(), handler);
      } else if (queryParam != null) {
        String raw = queryParameters.get(queryParam.value());
        if (raw == null || raw.isEmpty()) {
          if (queryParam.required()) {
            throw new ParamBindingException(
                "Query parameter '" + queryParam.value() + "' is required but was not provided");
          }
          args[i] = null;
        } else {
          args[i] = convert(raw, parameter.getType(), queryParam.pattern(),
              "query", queryParam.value(), handler);
        }
      } else {
        assert wildcardParam != null;
        args[i] = pathVariables.getOrDefault(RoutePattern.WILDCARD_VARIABLE, "");
      }
    }
    return args;
  }

  private static Object convert(String raw, Class<?> type, String pattern,
                                String location, String name, Method handler) {
    String described = describe(handler);
    try {
      if (type == String.class) {
        return raw;
      }
      if (type == boolean.class || type == Boolean.class) {
        if ("true".equalsIgnoreCase(raw)) {
          return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(raw)) {
          return Boolean.FALSE;
        }
        throw new ParamBindingException(failureMessage(location, name, raw, type)
            + ": expected 'true' or 'false'");
      }
      if (type == int.class || type == Integer.class) {
        return Integer.valueOf(raw.trim());
      }
      if (type == long.class || type == Long.class) {
        return Long.valueOf(raw.trim());
      }
      if (type == double.class || type == Double.class) {
        return Double.valueOf(raw.trim());
      }
      if (type == float.class || type == Float.class) {
        return Float.valueOf(raw.trim());
      }
      if (type == short.class || type == Short.class) {
        return Short.valueOf(raw.trim());
      }
      if (type == byte.class || type == Byte.class) {
        return Byte.valueOf(raw.trim());
      }
      if (Enum.class.isAssignableFrom(type)) {
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object value = Enum.valueOf((Class<? extends Enum>) type, raw);
        return value;
      }
      if (type == LocalDate.class) {
        return pattern.isEmpty() ? LocalDate.parse(raw)
            : LocalDate.parse(raw, DateTimeFormatter.ofPattern(pattern));
      }
      if (type == LocalTime.class) {
        return pattern.isEmpty() ? LocalTime.parse(raw)
            : LocalTime.parse(raw, DateTimeFormatter.ofPattern(pattern));
      }
      if (type == LocalDateTime.class) {
        return pattern.isEmpty() ? LocalDateTime.parse(raw)
            : LocalDateTime.parse(raw, DateTimeFormatter.ofPattern(pattern));
      }
      if (type == Instant.class) {
        return Instant.parse(raw);
      }
      throw new ParamBindingException("Unsupported parameter type " + type.getName()
          + " on " + described);
    } catch (ParamBindingException e) {
      throw e;
    } catch (NumberFormatException e) {
      throw new ParamBindingException(
          failureMessage(location, name, raw, type) + ": not a valid number", e);
    } catch (IllegalArgumentException e) {
      if (type.isEnum()) {
        throw new ParamBindingException(
            failureMessage(location, name, raw, type) + ": allowed values are "
                + allowedEnumConstants(type), e);
      }
      throw new ParamBindingException(
          failureMessage(location, name, raw, type) + ": " + e.getMessage(), e);
    } catch (DateTimeParseException e) {
      throw new ParamBindingException(
          failureMessage(location, name, raw, type) + ": unparseable date/time value", e);
    }
  }

  private static String failureMessage(String location, String name,
                                       String raw, Class<?> type) {
    return "Cannot bind " + location + " parameter '" + name + "' with value '"
        + raw + "' to " + typeName(type);
  }

  private static String typeName(Class<?> type) {
    return type.isPrimitive() ? type.getName() : type.getSimpleName();
  }

  private static String allowedEnumConstants(Class<?> type) {
    Object[] constants = type.getEnumConstants();
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < constants.length; i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(((Enum<?>) constants[i]).name());
    }
    return sb.append("]").toString();
  }

  private static void checkSupportedType(Class<?> type, Parameter parameter, Method handler) {
    boolean supported = type == String.class
        || type.isPrimitive()
        || SUPPORTED_NUMERIC.contains(type)
        || type == Boolean.class
        || type.isEnum()
        || type == LocalDate.class
        || type == LocalTime.class
        || type == LocalDateTime.class
        || type == Instant.class;
    if (!supported || type == char.class || type == Character.class) {
      throw new RouteRegistrationException(
          "Handler parameter '" + parameter.getName() + "' of " + describe(handler)
              + " has unsupported type " + type.getName());
    }
  }

  private static void checkPattern(String pattern, Class<?> type,
                                   Parameter parameter, Method handler) {
    if (pattern.isEmpty()) {
      return;
    }
    if (type == Instant.class) {
      throw new RouteRegistrationException(
          "Custom pattern is not supported for Instant parameter '"
              + parameter.getName() + "' of " + describe(handler)
              + "; Instant always uses ISO-8601");
    }
    if (type != LocalDate.class && type != LocalTime.class && type != LocalDateTime.class) {
      throw new RouteRegistrationException(
          "pattern() is only valid on date/time parameter '" + parameter.getName()
              + "' of " + describe(handler));
    }
    try {
      DateTimeFormatter.ofPattern(pattern);
    } catch (IllegalArgumentException e) {
      throw new RouteRegistrationException(
          "Invalid date/time pattern '" + pattern + "' on parameter '"
              + parameter.getName() + "' of " + describe(handler) + ": " + e.getMessage(), e);
    }
  }

  private static int countPresent(Annotation... annotations) {
    int count = 0;
    for (Annotation annotation : annotations) {
      if (annotation != null) {
        count++;
      }
    }
    return count;
  }

  private static String describe(Method handler) {
    return handler.getDeclaringClass().getSimpleName() + "#" + handler.getName();
  }
}

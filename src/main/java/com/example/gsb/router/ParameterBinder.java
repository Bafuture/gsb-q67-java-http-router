package com.example.gsb.router;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;

/**
 * Binds path variables and query parameters to the parameters of a handler method.
 *
 * <p>Every handler parameter must carry either {@link PathVariable} or
 * {@link RequestParam}; this is validated when the route is registered.
 */
final class ParameterBinder {

    private ParameterBinder() {
    }

    static void validate(Method handler, PathPattern pattern) {
        for (Parameter parameter : handler.getParameters()) {
            PathVariable pathVariable = parameter.getAnnotation(PathVariable.class);
            RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
            if (pathVariable == null && requestParam == null) {
                throw new IllegalArgumentException(
                        "Parameter '" + parameter.getName() + "' of " + handler
                                + " must be annotated with @PathVariable or @RequestParam");
            }
            if (pathVariable != null && requestParam != null) {
                throw new IllegalArgumentException(
                        "Parameter '" + parameter.getName() + "' of " + handler
                                + " must not carry both @PathVariable and @RequestParam");
            }
            if (!TypeConverter.supports(parameter.getType())) {
                throw new IllegalArgumentException(
                        "Parameter '" + parameter.getName() + "' of " + handler
                                + " has unsupported type " + parameter.getType().getName());
            }
        }
    }

    static Object[] bind(Method handler, Map<String, String> pathVariables, Map<String, String> queryParams) {
        Parameter[] parameters = handler.getParameters();
        Object[] args = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            PathVariable pathVariable = parameter.getAnnotation(PathVariable.class);
            if (pathVariable != null) {
                String name = pathVariable.value();
                String raw = pathVariables.get(name);
                if (raw == null) {
                    throw new ParameterBindingException("Parameter '" + name
                            + "' has no matching path variable in pattern of " + handler.getName());
                }
                args[i] = TypeConverter.convert(name, raw, parameter.getType());
                continue;
            }
            RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
            String name = requestParam.value();
            String raw = queryParams.get(name);
            if (raw == null && requestParam.required()) {
                throw new ParameterBindingException(
                        "Missing required query parameter '" + name + "'");
            }
            args[i] = TypeConverter.convert(name, raw, parameter.getType());
        }
        return args;
    }
}

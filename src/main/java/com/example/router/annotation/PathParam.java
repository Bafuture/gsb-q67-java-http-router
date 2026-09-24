package com.example.router.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds a handler parameter to a named path variable, e.g. the {@code id}
 * segment in {@code /users/{id}}.
 *
 * <p>Path parameters are always required (the segment is part of the path).
 * For {@code java.time} parameter types, {@link #pattern()} selects a
 * {@link java.time.format.DateTimeFormatter} pattern; otherwise ISO format
 * is used.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface PathParam {

  String value();

  String pattern() default "";
}

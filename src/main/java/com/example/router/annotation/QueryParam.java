package com.example.router.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds a handler parameter to a query string parameter ({@code ?name=value}).
 *
 * <p>When {@link #required()} is true (the default) a missing or
 * blank-value parameter fails the request. When false, a missing parameter
 * is passed as {@code null}; the declared type must then be a reference
 * type (a primitive cannot be null). For {@code java.time} parameter types,
 * {@link #pattern()} selects a custom formatter pattern.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface QueryParam {

  String value();

  boolean required() default true;

  String pattern() default "";
}

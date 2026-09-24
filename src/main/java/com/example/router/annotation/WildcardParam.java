package com.example.router.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds a handler parameter to the remainder captured by a {@code /**}
 * wildcard suffix, e.g. {@code a/b/c.txt} for {@code /files/**} matched by
 * {@code /files/a/b/c.txt}. The parameter must be of type {@code String};
 * the captured remainder may be empty.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface WildcardParam {
}

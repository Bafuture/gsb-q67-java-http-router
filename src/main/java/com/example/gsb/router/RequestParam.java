package com.example.gsb.router;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Binds a handler method parameter to a query parameter of the request. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface RequestParam {
    String value();

    /** When false, a missing query parameter binds {@code null} (or the primitive default fails fast). */
    boolean required() default true;
}

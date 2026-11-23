package com.spirecentral.dfcbridge.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Adds the error responses every endpoint has to the OpenAPI document of the annotated
 * controller or handler method, and with {@code session} the error responses of an endpoint
 * that runs on a DFC session.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ErrorResponses {

    boolean session() default false;
}

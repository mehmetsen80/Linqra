package org.lite.gateway.validation.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidAction {
    String message() default "Invalid action. Must be one of: delete, fetch, create, update, patch, options, head, generate";
} 
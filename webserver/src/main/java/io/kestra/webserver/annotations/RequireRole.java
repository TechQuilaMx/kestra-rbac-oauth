package io.kestra.webserver.annotations;

import io.kestra.webserver.models.auth.Role;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to require specific roles for controller methods
 * Example usage:
 * 
 * @RequireRole(Role.ADMIN)
 * public HttpResponse<?> deleteFlow(@PathVariable String flowId) { ... }
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
    /**
     * Required roles (OR logic - user must have at least one)
     */
    Role[] value();
    
    /**
     * Whether all roles are required (AND logic)
     */
    boolean requireAll() default false;
}

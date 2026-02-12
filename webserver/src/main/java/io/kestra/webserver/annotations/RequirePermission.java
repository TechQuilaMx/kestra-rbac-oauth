package io.kestra.webserver.annotations;

import io.kestra.webserver.models.auth.Permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to require specific permissions for controller methods
 * Example usage:
 * 
 * @RequirePermission(Permission.FLOWS_CREATE)
 * public HttpResponse<?> createFlow(@Body FlowRequest flow) { ... }
 * 
 * @RequirePermission({Permission.FLOWS_VIEW, Permission.FLOWS_EDIT})
 * public HttpResponse<?> editFlow(@PathVariable String flowId, @Body FlowRequest flow) { ... }
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
    /**
     * Required permissions (OR logic - user must have at least one)
     */
    Permission[] value();
    
    /**
     * Whether all permissions are required (AND logic)
     */
    boolean requireAll() default false;
}

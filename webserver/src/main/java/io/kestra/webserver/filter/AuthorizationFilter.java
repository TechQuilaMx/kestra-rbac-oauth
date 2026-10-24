package io.kestra.webserver.filter;

import io.kestra.webserver.annotations.RequirePermission;
import io.kestra.webserver.annotations.RequireRole;
import io.kestra.webserver.models.auth.Permission;
import io.kestra.webserver.models.auth.Role;
import io.kestra.webserver.models.auth.UserInfo;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.filter.ServerFilterPhase;
import io.micronaut.web.router.MethodBasedRouteMatch;
import io.micronaut.web.router.RouteMatch;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Optional;
import java.lang.reflect.Method;

/**
 * Authorization filter to check role and permission annotations
 * Must run after AuthenticationFilter (higher order number)
 */
@Slf4j
@Filter("/api/v1/**")
@Requires(property = "kestra.server-type", pattern = "(WEBSERVER|STANDALONE)")
@Requires(property = "kestra.server.oauth2.enabled", value = "true")
@Requires(property = "micronaut.security.enabled", notEquals = "true") // don't add this filter in EE
public class AuthorizationFilter implements HttpServerFilter {
    private static final Integer ORDER = ServerFilterPhase.SECURITY.order() + 10;

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        // Get user info from request (set by AuthenticationFilter)
        Optional<UserInfo> userInfoOpt = request.getAttribute("userInfo", UserInfo.class);
        
        if (userInfoOpt.isEmpty()) {
            // No OAuth2 authentication, skip authorization (BasicAuth doesn't use this)
            return chain.proceed(request);
        }
        
        UserInfo userInfo = userInfoOpt.get();
        
        // Get route match to check annotations
        RouteMatch<?> routeMatch = request.getAttribute("micronaut.http.route", RouteMatch.class).orElse(null);
        if (routeMatch == null || !(routeMatch instanceof MethodBasedRouteMatch<?, ?> methodMatch)) {
            return chain.proceed(request);
        }
        
        MethodBasedRouteMatch<?, ?> method = (MethodBasedRouteMatch<?, ?>) routeMatch;
        
        // Get the actual method and class using reflection
        Method targetMethod = method.getTargetMethod();
        Class<?> targetClass = targetMethod.getDeclaringClass();
        
        // Check @RequireRole annotation on method first, then class
        RequireRole requireRole = targetMethod.getAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = targetClass.getAnnotation(RequireRole.class);
        }
        
        if (requireRole != null) {
            if (!checkRoleRequirement(userInfo, requireRole.value(), requireRole.requireAll())) {
                log.warn("User {} does not have required role for {}", 
                    userInfo.getUsername(), request.getPath());
                return Mono.just(HttpResponse.status(HttpStatus.FORBIDDEN)
                    .body("{\"error\":\"Insufficient permissions\"}"));
            }
        }
        
        // Check @RequirePermission annotation on method first, then class
        RequirePermission requirePermission = targetMethod.getAnnotation(RequirePermission.class);
        if (requirePermission == null) {
            requirePermission = targetClass.getAnnotation(RequirePermission.class);
        }
        
        if (requirePermission != null) {
            if (!checkPermissionRequirement(userInfo, requirePermission.value(), requirePermission.requireAll())) {
                log.warn("User {} does not have required permission for {}", 
                    userInfo.getUsername(), request.getPath());
                return Mono.just(HttpResponse.status(HttpStatus.FORBIDDEN)
                    .body("{\"error\":\"Insufficient permissions\"}"));
            }
        }
        
        // Authorization passed, proceed
        return chain.proceed(request);
    }
    
    private boolean checkRoleRequirement(UserInfo userInfo, Role[] requiredRoles, boolean requireAll) {
        if (requireAll) {
            // User must have ALL roles
            return Arrays.stream(requiredRoles)
                .allMatch(userInfo::hasRole);
        } else {
            // User must have at least ONE role (OR logic)
            return Arrays.stream(requiredRoles)
                .anyMatch(userInfo::hasRole);
        }
    }
    
    private boolean checkPermissionRequirement(UserInfo userInfo, Permission[] requiredPermissions, boolean requireAll) {
        if (requireAll) {
            // User must have ALL permissions
            return userInfo.hasAllPermissions(requiredPermissions);
        } else {
            // User must have at least ONE permission (OR logic)
            return userInfo.hasAnyPermission(requiredPermissions);
        }
    }
}

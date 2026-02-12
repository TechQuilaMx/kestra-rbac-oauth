package io.kestra.webserver.controllers.api;

import io.kestra.webserver.models.auth.UserInfo;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Controller for user information and profile
 */
@Tag(name = "Users")
@Controller("/api/v1/user")
@Requires(property = "kestra.server-type", pattern = "(WEBSERVER|STANDALONE)")
public class UserController {
    
    @Get("/me")
    @Operation(tags = {"Users"}, summary = "Get current user information")
    public HttpResponse<?> getCurrentUser(HttpRequest<?> request) {
        // Get user info from request (set by AuthenticationFilter)
        Optional<UserInfo> userInfoOpt = request.getAttribute("userInfo", UserInfo.class);
        
        if (userInfoOpt.isEmpty()) {
            // BasicAuth or no authentication
            return HttpResponse.ok(Map.of(
                "authenticated", false
            ));
        }
        
        UserInfo userInfo = userInfoOpt.get();
        
        // Return user info without internal details
        return HttpResponse.ok(Map.of(
            "authenticated", true,
            "username", userInfo.getUsername(),
            "email", userInfo.getEmail() != null ? userInfo.getEmail() : "",
            "name", userInfo.getName() != null ? userInfo.getName() : "",
            "roles", userInfo.getRoles().stream()
                .map(role -> role.name().toLowerCase())
                .collect(Collectors.toList()),
            "permissions", userInfo.getPermissions().stream()
                .map(perm -> perm.getValue())
                .collect(Collectors.toList()),
            "isAdmin", userInfo.isAdmin()
        ));
    }
}

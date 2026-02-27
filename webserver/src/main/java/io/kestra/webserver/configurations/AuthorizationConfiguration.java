package io.kestra.webserver.configurations;

import io.kestra.webserver.models.auth.Permission;
import io.kestra.webserver.models.auth.Role;
import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

/**
 * Configuration for role-based permissions
 * Admins have all permissions by default
 * Operators have limited permissions that can be configured
 */
@Getter
@Setter
@ConfigurationProperties("kestra.server.authorization")
public class AuthorizationConfiguration {
    
    /**
     * Default permissions for OPERATOR role
     */
    private List<String> operatorPermissions = Arrays.asList(
        "flows.view",
        "executions.view",
        "executions.create",
        "templates.view",
        "namespaces.view",
        "kv.view",
        "settings.view"
    );
    
    /**
     * Get permissions for a role
     */
    public Set<Permission> getPermissionsForRole(Role role) {
        if (role == Role.ADMIN) {
            // Admin has all permissions
            return EnumSet.allOf(Permission.class);
        } else if (role == Role.OPERATOR) {
            // Operator has configured permissions
            Set<Permission> permissions = new HashSet<>();
            for (String permString : operatorPermissions) {
                Permission perm = Permission.fromString(permString);
                if (perm != null) {
                    permissions.add(perm);
                }
            }
            return permissions;
        }
        return Collections.emptySet();
    }
    
    /**
     * Get all permissions for a list of roles
     */
    public Set<Permission> getPermissionsForRoles(List<Role> roles) {
        Set<Permission> allPermissions = new HashSet<>();
        if (roles != null) {
            for (Role role : roles) {
                allPermissions.addAll(getPermissionsForRole(role));
            }
        }
        return allPermissions;
    }
}

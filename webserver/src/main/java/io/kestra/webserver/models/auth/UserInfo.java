package io.kestra.webserver.models.auth;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Set;

/**
 * User information extracted from OAuth2 token
 */
@Value
@Builder
public class UserInfo {
    String username;
    String email;
    String name;
    List<Role> roles;
    Set<Permission> permissions;
    
    /**
     * Check if user has a specific role
     */
    public boolean hasRole(Role role) {
        return roles != null && roles.contains(role);
    }
    
    /**
     * Check if user has admin role
     */
    public boolean isAdmin() {
        return hasRole(Role.ADMIN);
    }
    
    /**
     * Check if user has a specific permission
     */
    public boolean hasPermission(Permission permission) {
        return permissions != null && permissions.contains(permission);
    }
    
    /**
     * Check if user has any of the specified permissions
     */
    public boolean hasAnyPermission(Permission... requiredPermissions) {
        if (permissions == null) {
            return false;
        }
        for (Permission permission : requiredPermissions) {
            if (permissions.contains(permission)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if user has all of the specified permissions
     */
    public boolean hasAllPermissions(Permission... requiredPermissions) {
        if (permissions == null) {
            return false;
        }
        for (Permission permission : requiredPermissions) {
            if (!permissions.contains(permission)) {
                return false;
            }
        }
        return true;
    }
}

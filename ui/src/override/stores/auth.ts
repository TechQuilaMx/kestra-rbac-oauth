import {defineStore} from "pinia";
import {useOAuth2Store} from "../../stores/oauth2";

/**
 * User class that bridges the old auth system with new RBAC permissions
 */
export class Me {
    private oauth2Store: ReturnType<typeof useOAuth2Store>;

    constructor() {
        this.oauth2Store = useOAuth2Store();
    }

    /**
     * Map old permission enum to new RBAC permissions
     */
    private mapPermission(permission: string, action: string): string {
        const permissionMap: Record<string, Record<string, string>> = {
            "FLOW": {
                "READ": "flows.view",
                "CREATE": "flows.create",
                "UPDATE": "flows.edit",
                "DELETE": "flows.delete"
            },
            "EXECUTION": {
                "READ": "executions.view",
                "CREATE": "executions.create",
                "UPDATE": "executions.restart",
                "DELETE": "executions.kill"
            },
            "TEMPLATE": {
                "READ": "templates.view",
                "CREATE": "templates.create",
                "UPDATE": "templates.edit",
                "DELETE": "templates.delete"
            },
            "NAMESPACE": {
                "READ": "namespaces.view",
                "CREATE": "namespaces.create",
                "UPDATE": "namespaces.edit",
                "DELETE": "namespaces.delete"
            },
            "NAMESPACE_FILE": {
                "READ": "namespaceFiles.view",
                "CREATE": "namespaceFiles.create",
                "UPDATE": "namespaceFiles.edit",
                "DELETE": "namespaceFiles.delete"
            },
            "KV": {
                "READ": "kv.view",
                "CREATE": "kv.create",
                "UPDATE": "kv.edit",
                "DELETE": "kv.delete"
            },
            "SECRET": {
                "READ": "secrets.view",
                "CREATE": "secrets.create",
                "UPDATE": "secrets.edit",
                "DELETE": "secrets.delete"
            },
            "DASHBOARD": {
                "READ": "admin.dashboard",
                "CREATE": "admin.dashboard",
                "UPDATE": "admin.dashboard",
                "DELETE": "admin.dashboard"
            },
            "PLUGIN": {
                "READ": "admin.plugins"
            },
            "GROUP": {
                "READ": "admin.groups",
                "CREATE": "admin.groups",
                "UPDATE": "admin.groups",
                "DELETE": "admin.groups"
            },
            "SETTING": {
                "READ": "settings.view",
                "UPDATE": "settings.edit"
            }
        };

        const permissionKey = permissionMap[permission]?.[action];
        return permissionKey || "";
    }

    /**
     * Check if user has any permission for a namespace
     */
    hasAny(_permission: any, _namespace?: any) {
        // If user is admin, they have all permissions
        if (this.oauth2Store.isAdmin) {
            return true;
        }
        
        // For now, return true if user has any roles (maintains backward compatibility)
        // In a stricter implementation, this would check specific permissions
        return this.hasAnyRole();
    }

    /**
     * Check if user has a specific action permission on a namespace
     */
    hasAnyAction(permission: any, action: any, _namespace?: any) {
        // If user is admin, they have all permissions
        if (this.oauth2Store.isAdmin) {
            return true;
        }
        
        // Map old permission to new RBAC permission
        const rbacPermission = this.mapPermission(permission, action);
        if (!rbacPermission) {
            return false;
        }
        
        // Check if user has the permission
        return this.oauth2Store.hasPermission(rbacPermission);
    }

    /**
     * Check if user is allowed to perform an action on a specific namespace
     */
    isAllowed(permission: any, action: any, _namespace: any) {
        // If user is admin, they have all permissions
        if (this.oauth2Store.isAdmin) {
            return true;
        }
        
        // Map old permission to new RBAC permission
        const rbacPermission = this.mapPermission(permission, action);
        if (!rbacPermission) {
            return false;
        }
        
        // Check if user has the permission
        return this.oauth2Store.hasPermission(rbacPermission);
    }

    /**
     * Check if user is allowed globally (no namespace check)
     */
    isAllowedGlobal(permission: any, action: any) {
        // If user is admin, they have all permissions
        if (this.oauth2Store.isAdmin) {
            return true;
        }
        
        // Map old permission to new RBAC permission
        const rbacPermission = this.mapPermission(permission, action);
        if (!rbacPermission) {
            return false;
        }
        
        // Check if user has the permission
        return this.oauth2Store.hasPermission(rbacPermission);
    }

    /**
     * Check if user has any action permission across all namespaces
     */
    hasAnyActionOnAnyNamespace(permission: any, action: any) {
        // If user is admin, they have all permissions
        if (this.oauth2Store.isAdmin) {
            return true;
        }
        
        // Map old permission to new RBAC permission
        const rbacPermission = this.mapPermission(permission, action);
        if (!rbacPermission) {
            return false;
        }
        
        // Check if user has the permission
        return this.oauth2Store.hasPermission(rbacPermission);
    }

    /**
     * Check if user has any role
     */
    hasAnyRole() {
        return (this.oauth2Store.userInfo?.roles?.length ?? 0) > 0;
    }

    /**
     * Get namespaces where user can perform an action
     * In RBAC model without namespace-level permissions, return empty array or all namespaces for admins
     */
    getNamespacesForAction(permission: any, action: any): string[] {
        // If user is admin, they can access all namespaces
        if (this.oauth2Store.isAdmin) {
            return ["*"];
        }
        
        // Map old permission to new RBAC permission
        const rbacPermission = this.mapPermission(permission, action);
        if (!rbacPermission) {
            return [];
        }
        
        // If user has the permission, return wildcard (all namespaces)
        // In a more sophisticated implementation, this could return specific namespaces
        if (this.oauth2Store.hasPermission(rbacPermission)) {
            return ["*"];
        }
        
        return [];
    }
}

export const useAuthStore = defineStore("auth", {
    state: () => ({
        user: new Me(),
        isLogged: true,
    }),
    getters: {
        /**
         * Check if user is authenticated via OAuth2
         */
        isAuthenticated(): boolean {
            const oauth2Store = useOAuth2Store();
            return oauth2Store.isAuthenticated;
        }
    },
    actions: {
        logout() {
            const oauth2Store = useOAuth2Store();
            return oauth2Store.logout();
        },
        correction() {
            return Promise.resolve(true)
        }
    },
})

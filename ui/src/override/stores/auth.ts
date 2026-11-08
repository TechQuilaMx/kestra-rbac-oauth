import {defineStore} from "pinia"
import {useOAuth2Store} from "../../stores/oauth2"

/**
 * User class that bridges the old auth system with new RBAC permissions
 */
export class Me {
    private oauth2Store: ReturnType<typeof useOAuth2Store>

    constructor() {
        this.oauth2Store = useOAuth2Store()
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
                "READ": "dashboards.view",
                "CREATE": "dashboards.create",
                "UPDATE": "dashboards.edit",
                "DELETE": "dashboards.delete"
            },
            "PLUGIN": {
                "READ": "settings.view"
            },
            "SETTING": {
                "READ": "settings.view",
                "UPDATE": "settings.edit"
            }
        }

        const permissionKey = permissionMap[permission]?.[action]
        return permissionKey || ""
    }

    /**
     * Check if user has any permission for a namespace
     */
    hasAny(_permission: any, _namespace?: any) {
        if (this.oauth2Store.isAdmin) {
            return true
        }
        return this.hasAnyRole()
    }

    /**
     * Check if user has a specific action permission on a namespace
     */
    hasAnyAction(permission: any, action: any, _namespace?: any) {
        if (this.oauth2Store.isAdmin) {
            return true
        }
        const rbacPermission = this.mapPermission(permission, action)
        if (!rbacPermission) {
            return false
        }
        return this.oauth2Store.hasPermission(rbacPermission)
    }

    /**
     * Check if user is allowed to perform an action on a specific namespace
     */
    isAllowed(permission: any, action: any, _namespace: any) {
        if (this.oauth2Store.isAdmin) {
            return true
        }
        const rbacPermission = this.mapPermission(permission, action)
        if (!rbacPermission) {
            return false
        }
        return this.oauth2Store.hasPermission(rbacPermission)
    }

    /**
     * Check if user is allowed globally (no namespace check)
     */
    isAllowedGlobal(permission: any, action: any) {
        if (this.oauth2Store.isAdmin) {
            return true
        }
        const rbacPermission = this.mapPermission(permission, action)
        if (!rbacPermission) {
            return false
        }
        return this.oauth2Store.hasPermission(rbacPermission)
    }

    /**
     * Check if user has any action permission across all namespaces
     */
    hasAnyActionOnAnyNamespace(permission: any, action: any) {
        if (this.oauth2Store.isAdmin) {
            return true
        }
        const rbacPermission = this.mapPermission(permission, action)
        if (!rbacPermission) {
            return false
        }
        return this.oauth2Store.hasPermission(rbacPermission)
    }

    /**
     * Check if user has any role
     */
    hasAnyRole() {
        return (this.oauth2Store.userInfo?.roles?.length ?? 0) > 0
    }

    /**
     * Get namespaces where user can perform an action
     */
    getNamespacesForAction(permission: any, action: any): string[] {
        if (this.oauth2Store.isAdmin) {
            return ["*"]
        }
        const rbacPermission = this.mapPermission(permission, action)
        if (!rbacPermission) {
            return []
        }
        if (this.oauth2Store.hasPermission(rbacPermission)) {
            return ["*"]
        }
        return []
    }
}

export interface AuthMethods {
    mailsEnabled?: boolean
    passwordless?: boolean
    loginPassword?: boolean
    oauths?: string[]
}

export const useAuthStore = defineStore("auth", {
    state: () => ({
        user: new Me() as Me | undefined,
        isLogged: true,
        auths: undefined as AuthMethods | undefined,
    }),
    getters: {
        isAuthenticated(): boolean {
            const oauth2Store = useOAuth2Store()
            return oauth2Store.isAuthenticated
        }
    },
    actions: {
        logout() {
            const oauth2Store = useOAuth2Store()
            return oauth2Store.logout()
        },
        correction() {
            return Promise.resolve(true)
        },
        loadAuths(_options: any): Promise<AuthMethods | undefined> {
            return Promise.resolve(undefined)
        },
    },
})

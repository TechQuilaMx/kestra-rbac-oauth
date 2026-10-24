/**
 * Composable for checking user permissions and roles
 * Usage in components:
 * 
 * <script setup>
 * import { usePermissions } from '@/composables/usePermissions';
 * 
 * const { hasPermission, hasRole, isAdmin, canCreateFlows } = usePermissions();
 * </script>
 * 
 * <template>
 *   <button v-if="canCreateFlows">Create Flow</button>
 *   <button v-if="hasPermission('flows.delete')">Delete</button>
 * </template>
 */

import { computed } from 'vue';
import { useOAuth2Store } from '@/stores/oauth2';

export function usePermissions() {
    const oauth2Store = useOAuth2Store();

    /**
     * Check if user has a specific permission
     */
    const hasPermission = (permission: string): boolean => {
        return oauth2Store.hasPermission(permission);
    };

    /**
     * Check if user has any of the specified permissions
     */
    const hasAnyPermission = (...permissions: string[]): boolean => {
        return permissions.some(p => oauth2Store.hasPermission(p));
    };

    /**
     * Check if user has all of the specified permissions
     */
    const hasAllPermissions = (...permissions: string[]): boolean => {
        return permissions.every(p => oauth2Store.hasPermission(p));
    };

    /**
     * Check if user has a specific role
     */
    const hasRole = (role: string): boolean => {
        return oauth2Store.hasRole(role);
    };

    /**
     * Check if user is admin
     */
    const isAdmin = computed(() => oauth2Store.isAdmin);

    /**
     * Common permission checks
     */
    const canCreateFlows = computed(() => hasPermission('flows.create'));
    const canEditFlows = computed(() => hasPermission('flows.edit'));
    const canDeleteFlows = computed(() => hasPermission('flows.delete'));
    const canViewFlows = computed(() => hasPermission('flows.view'));
    
    const canCreateExecutions = computed(() => hasPermission('executions.create'));
    const canRestartExecutions = computed(() => hasPermission('executions.restart'));
    const canKillExecutions = computed(() => hasPermission('executions.kill'));
    const canViewExecutions = computed(() => hasPermission('executions.view'));
    
    const canAccessAdmin = computed(() => hasPermission('admin.access'));
    const canEditSettings = computed(() => hasPermission('settings.edit'));

    return {
        // Base functions
        hasPermission,
        hasAnyPermission,
        hasAllPermissions,
        hasRole,
        isAdmin,
        
        // Flow permissions
        canCreateFlows,
        canEditFlows,
        canDeleteFlows,
        canViewFlows,
        
        // Execution permissions
        canCreateExecutions,
        canRestartExecutions,
        canKillExecutions,
        canViewExecutions,
        
        // Admin permissions
        canAccessAdmin,
        canEditSettings,
    };
}

/**
 * Vue directive for permission-based element visibility
 * 
 * Usage in templates:
 * 
 * // Show element only if user has permission
 * <button v-permission="'flows.create'">Create Flow</button>
 * 
 * // Show element only if user has role
 * <button v-role="'admin'">Admin Panel</button>
 * 
 * // Show element only if user has any of the permissions
 * <button v-permission="['flows.edit', 'flows.delete']">Manage</button>
 */

import type { Directive } from 'vue';
import { useOAuth2Store } from '../stores/oauth2';

/**
 * v-permission directive
 * Removes element from DOM if user doesn't have the required permission(s)
 */
export const vPermission: Directive = {
    mounted(el, binding) {
        const oauth2Store = useOAuth2Store();
        const permission = binding.value;
        
        if (!permission) {
            return;
        }
        
        let hasPermission = false;
        
        if (Array.isArray(permission)) {
            // Check if user has any of the permissions
            hasPermission = permission.some(p => oauth2Store.hasPermission(p));
        } else {
            // Check single permission
            hasPermission = oauth2Store.hasPermission(permission);
        }
        
        if (!hasPermission) {
            // Remove element from DOM
            el.style.display = 'none';
        }
    },
};

/**
 * v-role directive
 * Removes element from DOM if user doesn't have the required role(s)
 */
export const vRole: Directive = {
    mounted(el, binding) {
        const oauth2Store = useOAuth2Store();
        const role = binding.value;
        
        if (!role) {
            return;
        }
        
        let hasRole = false;
        
        if (Array.isArray(role)) {
            // Check if user has any of the roles
            hasRole = role.some(r => oauth2Store.hasRole(r));
        } else {
            // Check single role
            hasRole = oauth2Store.hasRole(role);
        }
        
        if (!hasRole) {
            // Remove element from DOM
            el.style.display = 'none';
        }
    },
};

/**
 * v-admin directive
 * Removes element from DOM if user is not admin
 */
export const vAdmin: Directive = {
    mounted(el) {
        const oauth2Store = useOAuth2Store();
        
        if (!oauth2Store.isAdmin) {
            // Remove element from DOM
            el.style.display = 'none';
        }
    },
};

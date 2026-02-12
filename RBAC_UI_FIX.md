# RBAC UI Integration Fix

## Problem Identified

After testing the RBAC implementation, you discovered that users with no roles could still access all components in the UI. The root cause was:

1. **Dummy Auth Store**: The `override/stores/auth.ts` was a placeholder implementation that always returned `true` for all permission checks
2. **Disconnected Systems**: The OAuth2 store had the real user roles and permissions, but the UI components were using the dummy auth store
3. **No Bridge**: There was no connection between the OAuth2 RBAC system and the existing UI permission checks

## Solution Implemented

Updated `/data/dev/open-source/kstr-sso/ui/src/override/stores/auth.ts` to bridge the old permission system with the new RBAC roles:

### Key Changes

1. **Import OAuth2 Store**: The auth store now uses `useOAuth2Store()` to access real user roles and permissions

2. **Permission Mapping**: Created a `mapPermission()` method that maps old permission checks to new RBAC permissions:
   ```typescript
   "FLOW" + "CREATE" → "flows.create"
   "FLOW" + "UPDATE" → "flows.edit"
   "EXECUTION" + "READ" → "executions.view"
   // ... etc
   ```

3. **Updated All Methods**: All permission check methods now:
   - Check if user is admin (admins get full access)
   - Map old permission to RBAC permission
   - Check if user has the RBAC permission via OAuth2 store

4. **Methods Updated**:
   - `hasAny()` - checks if user has any roles
   - `hasAnyAction()` - maps and checks RBAC permission
   - `isAllowed()` - maps and checks RBAC permission
   - `isAllowedGlobal()` - maps and checks RBAC permission  
   - `hasAnyActionOnAnyNamespace()` - maps and checks RBAC permission
   - `hasAnyRole()` - checks OAuth2 store for roles
   - `getNamespacesForAction()` - returns ["*"] if user has permission

## How It Works Now

### For Admin Users (with `kestra-admin` role):
- All permission checks return `true`
- Can see and use all UI components
- Has access to all features

### For Operator Users (with `kestra-operator` role):
- Permission checks use the RBAC permissions from backend
- Default operator permissions (from `application-override.yml`):
  - ✅ Can view flows, executions, templates, namespaces, KV, settings
  - ✅ Can create executions
  - ❌ Cannot create, edit, or delete flows
  - ❌ Cannot delete executions
  - ❌ Cannot access admin features

### For Users with No Roles:
- All permission checks return `false`
- UI components with `v-if="canCreate"`, `v-if="canDelete"`, etc. are hidden
- Cannot perform any actions

## Example UI Checks That Now Work

**In `Flows.vue`:**
```vue
<router-link :to="{name: 'flows/create'}" v-if="canCreate">
  <el-button :icon="Plus" type="primary">
    {{ t("create") }}
  </el-button>
</router-link>
```

Where `canCreate` is computed as:
```typescript
const canCreate = computed(() => 
  user.value?.hasAnyActionOnAnyNamespace(permission.FLOW, action.CREATE)
);
```

This now properly checks if the user has the `flows.create` permission.

## Testing

To test the fix:

1. **User with no roles**: Should see no Create/Edit/Delete buttons, cannot access restricted features
2. **Operator user**: Should see limited buttons based on operator permissions from config
3. **Admin user**: Should see all buttons and have full access

## Backend Integration

The backend authorization still works independently:
- Even if UI shows a button, the backend `/api/v1/flows` endpoint will return 403 if user lacks `flows.create` permission
- This provides defense in depth - UI hides controls for UX, backend enforces for security

## Permission Mapping Reference

| Old System | Action | New RBAC Permission |
|-----------|--------|-------------------|
| FLOW | READ | flows.view |
| FLOW | CREATE | flows.create |
| FLOW | UPDATE | flows.edit |
| FLOW | DELETE | flows.delete |
| EXECUTION | READ | executions.view |
| EXECUTION | CREATE | executions.create |
| EXECUTION | UPDATE | executions.restart |
| EXECUTION | DELETE | executions.kill |
| TEMPLATE | READ | templates.view |
| TEMPLATE | CREATE | templates.create |
| TEMPLATE | UPDATE | templates.edit |
| TEMPLATE | DELETE | templates.delete |
| NAMESPACE | * | namespaces.* |
| NAMESPACE_FILE | * | namespaceFiles.* |
| KV | * | kv.* |
| SECRET | * | secrets.* |
| DASHBOARD | * | admin.dashboard |
| PLUGIN | READ | admin.plugins |
| GROUP | * | admin.groups |
| SETTING | READ | settings.view |
| SETTING | UPDATE | settings.edit |

## Files Modified

- `/data/dev/open-source/kstr-sso/ui/src/override/stores/auth.ts` - Complete rewrite to integrate with OAuth2 RBAC

## Next Steps

1. Test with users having different roles
2. Verify all UI components correctly show/hide based on permissions
3. If needed, adjust operator permissions in `application-override.yml`
4. Consider adding more fine-grained namespace-level permissions in the future

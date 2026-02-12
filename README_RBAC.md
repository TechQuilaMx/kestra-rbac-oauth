# Role-Based Access Control (RBAC) Implementation

This document describes the role-based access control (RBAC) system implemented for Kestra, which works with OAuth2 authentication.

## Overview

The RBAC system provides fine-grained access control with two predefined roles:
- **ADMIN**: Full access to all features and permissions
- **OPERATOR**: Limited access based on configured permissions

Roles are managed in your OAuth2 provider (Keycloak, Auth0, Google Workspace, etc.) and extracted from OAuth2 tokens during authentication.

## Architecture

### Backend Components

1. **Role and Permission Models** (`webserver/src/main/java/io/kestra/webserver/models/auth/`)
   - `Role.java`: Enum defining available roles (ADMIN, OPERATOR)
   - `Permission.java`: Enum defining fine-grained permissions
   - `UserInfo.java`: User information with roles and permissions

2. **Authorization Configuration** (`webserver/src/main/java/io/kestra/webserver/configurations/AuthorizationConfiguration.java`)
   - Maps roles to permissions
   - Admins automatically have all permissions
   - Operator permissions are configurable via `application.yml`

3. **OAuth2Service Updates** (`webserver/src/main/java/io/kestra/webserver/services/OAuth2Service.java`)
   - Extracts roles from OAuth2 token claims
   - Supports multiple claim structures (Keycloak, Auth0, Azure AD, Google)
   - Maps roles to permissions using AuthorizationConfiguration

4. **Authorization Annotations** (`webserver/src/main/java/io/kestra/webserver/annotations/`)
   - `@RequireRole`: Restrict endpoint to specific roles
   - `@RequirePermission`: Restrict endpoint to specific permissions

5. **Authorization Filter** (`webserver/src/main/java/io/kestra/webserver/filter/AuthorizationFilter.java`)
   - Intercepts requests after authentication
   - Checks role/permission requirements
   - Returns 403 Forbidden if requirements not met

6. **User Controller** (`webserver/src/main/java/io/kestra/webserver/controllers/api/UserController.java`)
   - GET `/api/v1/user/me`: Returns current user info, roles, and permissions

### Frontend Components

1. **OAuth2 Store Updates** (`ui/src/stores/oauth2.ts`)
   - Fetches user info from `/api/v1/user/me` after authentication
   - Provides getters for role and permission checks
   - `hasRole(role)`, `hasPermission(permission)`, `isAdmin`

2. **Permission Composable** (`ui/src/composables/usePermissions.ts`)
   - Vue composable for permission checks in components
   - Provides common permission checks (canCreateFlows, canEditFlows, etc.)

3. **Permission Directives** (`ui/src/directives/permissions.ts`)
   - `v-permission`: Hide element if user lacks permission
   - `v-role`: Hide element if user lacks role
   - `v-admin`: Hide element if user is not admin

## Available Permissions

### Flow Permissions
- `flows.view`: View flows
- `flows.create`: Create new flows
- `flows.edit`: Edit existing flows
- `flows.delete`: Delete flows

### Execution Permissions
- `executions.view`: View executions
- `executions.create`: Create/trigger new executions
- `executions.restart`: Restart failed executions
- `executions.kill`: Kill running executions

### Template Permissions
- `templates.view`: View templates
- `templates.create`: Create templates
- `templates.edit`: Edit templates
- `templates.delete`: Delete templates

### Namespace Permissions
- `namespaces.view`: View namespaces
- `namespaces.create`: Create namespaces
- `namespaces.edit`: Edit namespaces
- `namespaces.delete`: Delete namespaces

### KV Store Permissions
- `kv.view`: View KV store entries
- `kv.create`: Create KV store entries
- `kv.edit`: Edit KV store entries
- `kv.delete`: Delete KV store entries

### Secret Permissions
- `secrets.view`: View secrets
- `secrets.create`: Create secrets
- `secrets.edit`: Edit secrets
- `secrets.delete`: Delete secrets

### Admin Permissions
- `admin.access`: Access admin panel
- `admin.stats`: View system statistics
- `admin.triggers`: Manage triggers

### Settings Permissions
- `settings.view`: View settings
- `settings.edit`: Edit settings

## Configuration

### Backend Configuration

Add to `application-override.yml`:

```yaml
kestra:
  server:
    authorization:
      # Permissions for OPERATOR role (admins have all permissions)
      operator-permissions:
        - flows.view
        - executions.view
        - executions.create
        - templates.view
        - namespaces.view
        - kv.view
        - settings.view
```

Environment variables (for Docker/Kubernetes):

```bash
KESTRA_SERVER_AUTHORIZATION_OPERATOR_PERMISSIONS_0=flows.view
KESTRA_SERVER_AUTHORIZATION_OPERATOR_PERMISSIONS_1=executions.view
KESTRA_SERVER_AUTHORIZATION_OPERATOR_PERMISSIONS_2=executions.create
# ... add more as needed
```

### OAuth2 Provider Configuration

#### Keycloak Setup

1. **Create Roles** in your Keycloak realm:
   ```
   - Realm Roles → Add Role
   - Name: kestra-admin
   - Save
   
   - Realm Roles → Add Role
   - Name: kestra-operator
   - Save
   ```

2. **Assign Roles to Users**:
   ```
   - Users → Select User → Role Mappings
   - Available Roles: Select kestra-admin or kestra-operator
   - Add selected
   ```

3. **Configure Client Role Mapper**:
   ```
   - Clients → kestra-app → Client Scopes → kestra-app-dedicated
   - Add mapper → By configuration → User Realm Role
   - Name: realm-roles
   - Token Claim Name: realm_access.roles
   - Claim JSON Type: String
   - Add to ID token: ON
   - Add to access token: ON
   - Add to userinfo: ON
   ```

4. **Verify Token Contains Roles**:
   ```
   - Clients → kestra-app → Client Scopes → Evaluate
   - Select a test user
   - Generated Access Token should contain:
   {
     "realm_access": {
       "roles": ["kestra-admin"]
     }
   }
   ```

#### Auth0 Setup

1. **Create Roles**:
   ```
   - User Management → Roles → Create Role
   - Name: kestra-admin
   - Description: Kestra Administrator
   - Create
   
   - Create another role: kestra-operator
   ```

2. **Assign Roles to Users**:
   ```
   - User Management → Users → Select User
   - Roles → Assign Roles
   - Select kestra-admin or kestra-operator
   ```

3. **Create Action to Add Roles to Tokens**:
   ```
   - Actions → Library → Build Custom
   - Name: Add Roles to Tokens
   - Trigger: Login / Post Login
   - Code:
   
   exports.onExecutePostLogin = async (event, api) => {
     const namespace = 'https://kestra.io';
     if (event.authorization) {
       api.idToken.setCustomClaim(`${namespace}/roles`, event.authorization.roles);
       api.accessToken.setCustomClaim(`${namespace}/roles`, event.authorization.roles);
     }
   };
   
   - Deploy
   
   - Actions → Flows → Login → Add Action
   - Select "Add Roles to Tokens"
   ```

4. **Update Kestra Configuration**:
   ```yaml
   kestra:
     server:
       oauth2:
         role-claim-path: "https://kestra.io/roles"
   ```

#### Google Workspace / Azure AD

For Google Workspace and Azure AD, roles are typically provided via the `groups` claim:

```yaml
kestra:
  server:
    oauth2:
      role-claim-path: "groups"
```

Create groups named `kestra-admin` and `kestra-operator` in your identity provider and assign users to them.

## Backend Usage

### Protecting Controller Endpoints

Use `@RequireRole` or `@RequirePermission` annotations:

```java
import io.kestra.webserver.annotations.RequirePermission;
import io.kestra.webserver.models.auth.Permission;

@Controller("/api/v1/flows")
public class FlowController {
    
    @Post
    @RequirePermission(Permission.FLOWS_CREATE)
    public HttpResponse<Flow> createFlow(@Body Flow flow) {
        // Only users with flows.create permission can access
        return HttpResponse.ok(flowService.create(flow));
    }
    
    @Delete("/{id}")
    @RequirePermission(Permission.FLOWS_DELETE)
    public HttpResponse<Void> deleteFlow(@PathVariable String id) {
        // Only users with flows.delete permission can access
        flowService.delete(id);
        return HttpResponse.noContent();
    }
}
```

Using `@RequireRole` annotation:

```java
import io.kestra.webserver.annotations.RequireRole;
import io.kestra.webserver.models.auth.Role;

@Get("/admin/stats")
@RequireRole(Role.ADMIN)
public HttpResponse<Stats> getStats() {
    // Only admins can access
    return HttpResponse.ok(statsService.getStats());
}
```

Multiple roles (OR logic):

```java
@RequireRole({Role.ADMIN, Role.OPERATOR})
public HttpResponse<Flow> viewFlow(@PathVariable String id) {
    // User must have ADMIN OR OPERATOR role
    return HttpResponse.ok(flowService.get(id));
}
```

All roles required (AND logic):

```java
@RequireRole(value = {Role.ADMIN, Role.OPERATOR}, requireAll = true)
public HttpResponse<?> specialEndpoint() {
    // User must have BOTH ADMIN AND OPERATOR roles
    return HttpResponse.ok();
}
```

### Accessing User Info in Controllers

```java
import io.kestra.webserver.models.auth.UserInfo;

@Get("/flows")
public HttpResponse<List<Flow>> listFlows(HttpRequest<?> request) {
    Optional<UserInfo> userInfo = request.getAttribute("userInfo", UserInfo.class);
    
    if (userInfo.isPresent()) {
        String username = userInfo.get().getUsername();
        boolean isAdmin = userInfo.get().isAdmin();
        
        if (isAdmin) {
            // Return all flows for admin
            return HttpResponse.ok(flowService.findAll());
        } else {
            // Return only user's flows
            return HttpResponse.ok(flowService.findByUser(username));
        }
    }
    
    return HttpResponse.unauthorized();
}
```

## Frontend Usage

### Using Composable in Components

```vue
<script setup>
import { usePermissions } from '@/composables/usePermissions';

const { 
    hasPermission, 
    canCreateFlows, 
    canDeleteFlows,
    isAdmin 
} = usePermissions();

// Check permissions programmatically
const handleAction = () => {
    if (canCreateFlows.value) {
        // Perform action
    } else {
        showError("You don't have permission to create flows");
    }
};
</script>

<template>
    <div>
        <!-- Only show button if user has permission -->
        <button v-if="canCreateFlows" @click="createNewFlow">
            Create Flow
        </button>
        
        <button v-if="canDeleteFlows" @click="deleteFlow">
            Delete
        </button>
        
        <div v-if="isAdmin">
            Admin Panel
        </div>
    </div>
</template>
```

### Using Directives

```vue
<template>
    <div>
        <!-- Hide element if user doesn't have permission -->
        <button v-permission="'flows.create'" @click="createFlow">
            Create Flow
        </button>
        
        <!-- Multiple permissions (OR logic) -->
        <button v-permission="['flows.edit', 'flows.delete']" @click="manageFlow">
            Manage Flow
        </button>
        
        <!-- Check role -->
        <div v-role="'admin'">
            Admin-only content
        </div>
        
        <!-- Check if admin -->
        <button v-admin @click="openAdminPanel">
            Admin Panel
        </button>
    </div>
</template>
```

## Testing

### Test with Different Roles

1. **Create Test Users in Keycloak**:
   ```
   - admin-user@example.com → kestra-admin role
   - operator-user@example.com → kestra-operator role
   ```

2. **Test Admin User**:
   - Login as admin-user@example.com
   - Should see all buttons and menu items
   - Can create, edit, and delete flows
   - Can access admin panel

3. **Test Operator User**:
   - Login as operator-user@example.com
   - Should NOT see "Create Flow" button (flows.create permission missing)
   - Should NOT see "Delete" button (flows.delete permission missing)
   - Should see "View" and "Execute" options
   - Cannot access admin panel

4. **Test API Endpoints**:
   ```bash
   # Get access token for operator user
   OPERATOR_TOKEN="..."
   
   # This should succeed (operators can view)
   curl -H "Authorization: Bearer $OPERATOR_TOKEN" \
        http://localhost:8080/api/v1/flows
   
   # This should fail with 403 Forbidden (operators cannot create)
   curl -X POST \
        -H "Authorization: Bearer $OPERATOR_TOKEN" \
        -H "Content-Type: application/yaml" \
        --data-binary @flow.yml \
        http://localhost:8080/api/v1/flows
   ```

## Customizing Permissions

To give operators more permissions, update `application-override.yml`:

```yaml
kestra:
  server:
    authorization:
      operator-permissions:
        - flows.view
        - flows.create      # Allow operators to create flows
        - flows.edit        # Allow operators to edit flows
        - executions.view
        - executions.create
        - executions.restart # Allow operators to restart executions
        - templates.view
        - namespaces.view
        - kv.view
        - kv.create         # Allow operators to create KV entries
        - settings.view
```

Restart Kestra backend for changes to take effect.

## Adding Custom Roles

To add more roles beyond ADMIN and OPERATOR:

1. **Update Role Enum** (`Role.java`):
   ```java
   public enum Role {
       ADMIN("kestra-admin"),
       OPERATOR("kestra-operator"),
       DEVELOPER("kestra-developer"),  // New role
       VIEWER("kestra-viewer");         // New role
       
       // ... rest of code
   }
   ```

2. **Add Permission Mapping** (`AuthorizationConfiguration.java`):
   ```java
   public Set<Permission> getPermissionsForRole(Role role) {
       if (role == Role.ADMIN) {
           return EnumSet.allOf(Permission.class);
       } else if (role == Role.DEVELOPER) {
           return Set.of(
               Permission.FLOWS_VIEW,
               Permission.FLOWS_CREATE,
               Permission.FLOWS_EDIT,
               Permission.EXECUTIONS_VIEW,
               Permission.EXECUTIONS_CREATE
           );
       } else if (role == Role.VIEWER) {
           return Set.of(
               Permission.FLOWS_VIEW,
               Permission.EXECUTIONS_VIEW
           );
       } else if (role == Role.OPERATOR) {
           // ... operator permissions
       }
       return Collections.emptySet();
   }
   ```

3. **Create Roles in OAuth2 Provider**:
   - Keycloak: Create `kestra-developer` and `kestra-viewer` realm roles
   - Assign to users as needed

## Troubleshooting

### Roles Not Extracted from Token

**Problem**: User authenticated but has no roles.

**Solutions**:
1. Check token contains roles:
   ```bash
   # Decode JWT token at https://jwt.io
   # Look for "realm_access.roles" (Keycloak) or "roles" (Auth0) claim
   ```

2. Verify role claim path configuration:
   ```yaml
   kestra:
     server:
       oauth2:
         role-claim-path: "realm_access.roles"  # For Keycloak
         # or
         role-claim-path: "https://yourapp.com/roles"  # For Auth0
   ```

3. Enable debug logging:
   ```yaml
   logger:
     levels:
       io.kestra.webserver.services.OAuth2Service: DEBUG
       io.kestra.webserver.filter.AuthorizationFilter: DEBUG
   ```

### 403 Forbidden on API Calls

**Problem**: User gets 403 Forbidden error.

**Solutions**:
1. Check user has required permission:
   ```bash
   curl http://localhost:8080/api/v1/user/me
   # Should show user's roles and permissions
   ```

2. Verify permission mapping in `application-override.yml`

3. Check controller annotations match required permissions

### UI Elements Still Visible

**Problem**: Buttons/elements visible but API returns 403.

**Solution**: Frontend permission checks are for UX only. Backend always enforces permissions. This is correct behavior - always validate on backend.

## Security Considerations

1. **Backend Enforcement**: Always use `@RequireRole` or `@RequirePermission` annotations on controllers. Frontend checks are for UX only.

2. **Token Validation**: Roles are extracted from validated OAuth2 tokens. Invalid or expired tokens are rejected before role checks.

3. **Permission Changes**: Permission changes in `application.yml` require backend restart. Role assignment changes in OAuth provider take effect immediately on next login.

4. **Default Role**: If no roles found in token, user defaults to OPERATOR role with limited permissions.

5. **Admin Detection**: Admins automatically have all permissions. Check `isAdmin()` for admin-specific logic.

## Summary

The RBAC system provides:
- ✅ Provider-managed roles (OAuth2 provider is source of truth)
- ✅ Fine-grained permission control
- ✅ Configurable operator permissions
- ✅ Backend enforcement with annotations
- ✅ Frontend helpers for UX (composable, directives)
- ✅ Support for multiple OAuth2 providers
- ✅ Easy testing with different user roles

Admins have full access. Operators have limited access based on configured permissions. All access is validated on the backend for security.

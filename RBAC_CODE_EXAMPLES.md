# RBAC Code Examples

This document provides practical code examples for using the RBAC system in Kestra.

## Table of Contents
- [Backend Examples](#backend-examples)
  - [Protecting Controllers](#protecting-controllers)
  - [Accessing User Info](#accessing-user-info)
  - [Custom Permission Logic](#custom-permission-logic)
- [Frontend Examples](#frontend-examples)
  - [Using Composable](#using-composable)
  - [Using Directives](#using-directives)
  - [Conditional Rendering](#conditional-rendering)
  - [Navigation Guards](#navigation-guards)
- [Testing Examples](#testing-examples)

---

## Backend Examples

### Protecting Controllers

#### Basic Permission Check

```java
import io.kestra.webserver.annotations.RequirePermission;
import io.kestra.webserver.models.auth.Permission;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;

@Controller("/api/v1/flows")
public class FlowController {
    
    @Post
    @RequirePermission(Permission.FLOWS_CREATE)
    public HttpResponse<Flow> createFlow(@Body Flow flow) {
        // Only users with flows.create permission can access
        return HttpResponse.ok(flowService.create(flow));
    }
}
```

#### Multiple Permissions (OR Logic)

```java
@Get("/{id}")
@RequirePermission({Permission.FLOWS_VIEW, Permission.FLOWS_EDIT})
public HttpResponse<Flow> getFlow(@PathVariable String id) {
    // User needs EITHER flows.view OR flows.edit permission
    return HttpResponse.ok(flowService.findById(id));
}
```

#### Multiple Permissions (AND Logic)

```java
@Post("/{id}/publish")
@RequirePermission(value = {Permission.FLOWS_EDIT, Permission.ADMIN_ACCESS}, requireAll = true)
public HttpResponse<Flow> publishFlow(@PathVariable String id) {
    // User needs BOTH flows.edit AND admin.access permissions
    return HttpResponse.ok(flowService.publish(id));
}
```

#### Role-Based Protection

```java
import io.kestra.webserver.annotations.RequireRole;
import io.kestra.webserver.models.auth.Role;

@Get("/admin/stats")
@RequireRole(Role.ADMIN)
public HttpResponse<Stats> getSystemStats() {
    // Only admins can access
    return HttpResponse.ok(statsService.getStats());
}
```

#### Multiple Roles

```java
@Get("/flows")
@RequireRole({Role.ADMIN, Role.OPERATOR})
public HttpResponse<List<Flow>> listFlows() {
    // User needs ADMIN OR OPERATOR role
    return HttpResponse.ok(flowService.findAll());
}
```

#### Class-Level Protection

```java
@Controller("/api/v1/admin")
@RequireRole(Role.ADMIN)  // Applies to all methods in this controller
public class AdminController {
    
    @Get("/users")
    public HttpResponse<List<User>> listUsers() {
        // Automatically requires ADMIN role
        return HttpResponse.ok(userService.findAll());
    }
    
    @Post("/settings")
    public HttpResponse<Settings> updateSettings(@Body Settings settings) {
        // Automatically requires ADMIN role
        return HttpResponse.ok(settingsService.update(settings));
    }
}
```

### Accessing User Info

#### Get Current User in Controller

```java
import io.kestra.webserver.models.auth.UserInfo;
import io.micronaut.http.HttpRequest;

@Get("/flows")
public HttpResponse<List<Flow>> listFlows(HttpRequest<?> request) {
    Optional<UserInfo> userInfo = request.getAttribute("userInfo", UserInfo.class);
    
    if (userInfo.isPresent()) {
        String username = userInfo.get().getUsername();
        
        if (userInfo.get().isAdmin()) {
            // Return all flows for admin
            return HttpResponse.ok(flowService.findAll());
        } else {
            // Return only user's flows for non-admins
            return HttpResponse.ok(flowService.findByCreatedBy(username));
        }
    }
    
    return HttpResponse.unauthorized();
}
```

#### Check Permissions Manually

```java
@Get("/flows/{id}/actions")
public HttpResponse<FlowActions> getAvailableActions(
    @PathVariable String id,
    HttpRequest<?> request
) {
    Optional<UserInfo> userInfo = request.getAttribute("userInfo", UserInfo.class);
    
    if (userInfo.isEmpty()) {
        return HttpResponse.unauthorized();
    }
    
    UserInfo user = userInfo.get();
    Flow flow = flowService.findById(id);
    
    // Build list of available actions based on permissions
    FlowActions actions = FlowActions.builder()
        .canView(user.hasPermission(Permission.FLOWS_VIEW))
        .canEdit(user.hasPermission(Permission.FLOWS_EDIT))
        .canDelete(user.hasPermission(Permission.FLOWS_DELETE))
        .canExecute(user.hasPermission(Permission.EXECUTIONS_CREATE))
        .canExport(user.hasAnyPermission(Permission.FLOWS_VIEW, Permission.FLOWS_EDIT))
        .build();
    
    return HttpResponse.ok(actions);
}
```

### Custom Permission Logic

#### Dynamic Permission Checking

```java
@Service
public class FlowAuthorizationService {
    
    public boolean canAccessFlow(UserInfo user, Flow flow) {
        // Admins can access everything
        if (user.isAdmin()) {
            return true;
        }
        
        // Check if user has view permission
        if (!user.hasPermission(Permission.FLOWS_VIEW)) {
            return false;
        }
        
        // Additional business logic
        // e.g., check if flow belongs to user's namespace
        return flow.getNamespace().startsWith(user.getUsername());
    }
    
    public boolean canModifyFlow(UserInfo user, Flow flow) {
        if (user.isAdmin()) {
            return true;
        }
        
        // Must have edit permission AND be the creator
        return user.hasPermission(Permission.FLOWS_EDIT) 
            && flow.getCreatedBy().equals(user.getUsername());
    }
}

// In controller
@Put("/{id}")
@RequirePermission(Permission.FLOWS_EDIT)  // Basic check
public HttpResponse<Flow> updateFlow(
    @PathVariable String id,
    @Body Flow updates,
    HttpRequest<?> request
) {
    UserInfo user = request.getAttribute("userInfo", UserInfo.class).orElseThrow();
    Flow existingFlow = flowService.findById(id);
    
    // Additional authorization check
    if (!flowAuthService.canModifyFlow(user, existingFlow)) {
        return HttpResponse.status(HttpStatus.FORBIDDEN)
            .body("You can only modify your own flows");
    }
    
    return HttpResponse.ok(flowService.update(id, updates));
}
```

#### Permission-Based Filtering

```java
@Get("/flows")
public HttpResponse<List<Flow>> listFlows(HttpRequest<?> request) {
    UserInfo user = request.getAttribute("userInfo", UserInfo.class).orElseThrow();
    
    List<Flow> flows = flowService.findAll();
    
    // Filter based on user permissions
    if (!user.isAdmin()) {
        flows = flows.stream()
            .filter(flow -> canUserAccessFlow(user, flow))
            .collect(Collectors.toList());
    }
    
    return HttpResponse.ok(flows);
}

private boolean canUserAccessFlow(UserInfo user, Flow flow) {
    // Custom logic: can only see flows in accessible namespaces
    return user.hasPermission(Permission.FLOWS_VIEW) 
        && isNamespaceAccessible(user, flow.getNamespace());
}
```

---

## Frontend Examples

### Using Composable

#### Basic Component with Permission Checks

```vue
<script setup>
import { usePermissions } from '@/composables/usePermissions';
import { ref } from 'vue';

const { canCreateFlows, canEditFlows, canDeleteFlows, isAdmin } = usePermissions();
const flows = ref([]);

const handleCreate = () => {
    if (!canCreateFlows.value) {
        alert('You do not have permission to create flows');
        return;
    }
    // Create flow logic
};

const handleDelete = (flowId) => {
    if (!canDeleteFlows.value) {
        alert('You do not have permission to delete flows');
        return;
    }
    // Delete flow logic
};
</script>

<template>
    <div class="flow-list">
        <h1>Flows</h1>
        
        <button v-if="canCreateFlows" @click="handleCreate" class="btn-primary">
            Create New Flow
        </button>
        
        <div v-if="isAdmin" class="admin-panel">
            <h2>Admin Tools</h2>
            <button>Manage Users</button>
            <button>View Logs</button>
        </div>
        
        <table>
            <tr v-for="flow in flows" :key="flow.id">
                <td>{{ flow.name }}</td>
                <td>
                    <button v-if="canEditFlows">Edit</button>
                    <button v-if="canDeleteFlows" @click="handleDelete(flow.id)">
                        Delete
                    </button>
                </td>
            </tr>
        </table>
    </div>
</template>
```

#### Complex Permission Checking

```vue
<script setup>
import { usePermissions } from '@/composables/usePermissions';
import { computed } from 'vue';

const { 
    hasPermission, 
    hasAnyPermission, 
    hasAllPermissions,
    isAdmin 
} = usePermissions();

// Single permission check
const canExport = computed(() => hasPermission('flows.view'));

// Multiple permissions (OR logic)
const canManageFlow = computed(() => 
    hasAnyPermission('flows.edit', 'flows.delete')
);

// Multiple permissions (AND logic)
const canPublishFlow = computed(() => 
    hasAllPermissions('flows.edit', 'admin.access')
);

// Complex logic
const canAccessAdvancedFeatures = computed(() => {
    return isAdmin.value || (
        hasPermission('flows.edit') && 
        hasPermission('executions.create')
    );
});
</script>

<template>
    <div>
        <button v-if="canExport" @click="exportFlow">Export</button>
        <button v-if="canManageFlow" @click="manageFlow">Manage</button>
        <button v-if="canPublishFlow" @click="publishFlow">Publish</button>
        
        <div v-if="canAccessAdvancedFeatures" class="advanced">
            <h3>Advanced Features</h3>
            <!-- Advanced options -->
        </div>
    </div>
</template>
```

### Using Directives

#### Simple Directive Usage

```vue
<template>
    <div class="toolbar">
        <!-- Hide if user doesn't have permission -->
        <button v-permission="'flows.create'" @click="createFlow">
            New Flow
        </button>
        
        <button v-permission="'flows.edit'" @click="editFlow">
            Edit
        </button>
        
        <button v-permission="'flows.delete'" @click="deleteFlow">
            Delete
        </button>
    </div>
</template>
```

#### Multiple Permissions (OR Logic)

```vue
<template>
    <div>
        <!-- Show if user has ANY of these permissions -->
        <button v-permission="['flows.edit', 'flows.delete']" @click="manageFlow">
            Manage Flow
        </button>
        
        <!-- Show if user has admin OR operator role -->
        <div v-role="['admin', 'operator']">
            Welcome to the dashboard
        </div>
    </div>
</template>
```

#### Role-Based Directives

```vue
<template>
    <div>
        <!-- Show only for admins -->
        <div v-admin class="admin-panel">
            <h2>Admin Panel</h2>
            <button>User Management</button>
            <button>System Settings</button>
        </div>
        
        <!-- Show only for operators -->
        <div v-role="'operator'" class="operator-panel">
            <h2>Operator Tools</h2>
            <button>Run Flow</button>
            <button>View Logs</button>
        </div>
        
        <!-- Show for specific role -->
        <button v-role="'admin'" @click="openAdminPanel">
            Admin Settings
        </button>
    </div>
</template>
```

### Conditional Rendering

#### Dynamic Menu Based on Permissions

```vue
<script setup>
import { usePermissions } from '@/composables/usePermissions';
import { computed } from 'vue';

const {
    canViewFlows,
    canCreateFlows,
    canViewExecutions,
    canAccessAdmin,
    isAdmin
} = usePermissions();

const menuItems = computed(() => {
    const items = [];
    
    if (canViewFlows.value) {
        items.push({ label: 'Flows', path: '/flows', icon: 'flow' });
    }
    
    if (canCreateFlows.value) {
        items.push({ label: 'Create Flow', path: '/flows/new', icon: 'plus' });
    }
    
    if (canViewExecutions.value) {
        items.push({ label: 'Executions', path: '/executions', icon: 'play' });
    }
    
    if (canAccessAdmin.value) {
        items.push({ label: 'Admin', path: '/admin', icon: 'settings' });
    }
    
    return items;
});
</script>

<template>
    <nav class="sidebar">
        <ul>
            <li v-for="item in menuItems" :key="item.path">
                <router-link :to="item.path">
                    <i :class="`icon-${item.icon}`"></i>
                    {{ item.label }}
                </router-link>
            </li>
        </ul>
        
        <div v-if="isAdmin" class="admin-badge">
            Admin User
        </div>
    </nav>
</template>
```

#### Conditional Form Fields

```vue
<script setup>
import { usePermissions } from '@/composables/usePermissions';

const { hasPermission, isAdmin } = usePermissions();
const canSetAdvancedOptions = hasPermission('flows.edit');
const canSetSchedule = hasPermission('admin.triggers');
</script>

<template>
    <form>
        <!-- Everyone can see basic fields -->
        <input v-model="flow.name" placeholder="Flow Name" />
        <textarea v-model="flow.description" placeholder="Description" />
        
        <!-- Only users with flows.edit can set advanced options -->
        <div v-if="canSetAdvancedOptions" class="advanced">
            <h3>Advanced Options</h3>
            <input v-model="flow.timeout" type="number" placeholder="Timeout" />
            <input v-model="flow.retries" type="number" placeholder="Retries" />
        </div>
        
        <!-- Only admins can set schedules -->
        <div v-if="canSetSchedule" class="schedule">
            <h3>Schedule</h3>
            <input v-model="flow.cron" placeholder="Cron Expression" />
        </div>
        
        <button type="submit">Save</button>
    </form>
</template>
```

### Navigation Guards

#### Protect Routes Based on Permissions

```javascript
// In router configuration
import { useOAuth2Store } from '@/stores/oauth2';

const router = createRouter({
    routes: [
        {
            path: '/flows/new',
            component: CreateFlow,
            meta: { requiresPermission: 'flows.create' }
        },
        {
            path: '/admin',
            component: AdminPanel,
            meta: { requiresRole: 'admin' }
        }
    ]
});

router.beforeEach((to, from, next) => {
    const oauth2Store = useOAuth2Store();
    
    // Check permission requirement
    if (to.meta.requiresPermission) {
        if (!oauth2Store.hasPermission(to.meta.requiresPermission)) {
            return next({ name: 'forbidden' });
        }
    }
    
    // Check role requirement
    if (to.meta.requiresRole) {
        if (!oauth2Store.hasRole(to.meta.requiresRole)) {
            return next({ name: 'forbidden' });
        }
    }
    
    next();
});
```

---

## Testing Examples

### Backend Unit Tests

```java
import io.kestra.webserver.models.auth.Permission;
import io.kestra.webserver.models.auth.Role;
import io.kestra.webserver.models.auth.UserInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UserInfoTest {
    
    @Test
    void testAdminHasAllPermissions() {
        UserInfo admin = UserInfo.builder()
            .username("admin")
            .roles(List.of(Role.ADMIN))
            .permissions(Set.of(Permission.values()))
            .build();
        
        assertTrue(admin.isAdmin());
        assertTrue(admin.hasPermission(Permission.FLOWS_CREATE));
        assertTrue(admin.hasPermission(Permission.FLOWS_DELETE));
    }
    
    @Test
    void testOperatorHasLimitedPermissions() {
        UserInfo operator = UserInfo.builder()
            .username("operator")
            .roles(List.of(Role.OPERATOR))
            .permissions(Set.of(
                Permission.FLOWS_VIEW,
                Permission.EXECUTIONS_VIEW
            ))
            .build();
        
        assertFalse(operator.isAdmin());
        assertTrue(operator.hasPermission(Permission.FLOWS_VIEW));
        assertFalse(operator.hasPermission(Permission.FLOWS_CREATE));
    }
    
    @Test
    void testHasAnyPermission() {
        UserInfo user = UserInfo.builder()
            .username("user")
            .roles(List.of(Role.OPERATOR))
            .permissions(Set.of(Permission.FLOWS_VIEW))
            .build();
        
        assertTrue(user.hasAnyPermission(
            Permission.FLOWS_VIEW,
            Permission.FLOWS_EDIT
        ));
        
        assertFalse(user.hasAnyPermission(
            Permission.FLOWS_CREATE,
            Permission.FLOWS_DELETE
        ));
    }
}
```

### Frontend Unit Tests

```typescript
import { describe, it, expect, beforeEach } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useOAuth2Store } from '@/stores/oauth2';

describe('OAuth2 Store Permissions', () => {
    beforeEach(() => {
        setActivePinia(createPinia());
    });
    
    it('should check if user has permission', () => {
        const store = useOAuth2Store();
        store.userInfo = {
            authenticated: true,
            username: 'operator@example.com',
            roles: ['operator'],
            permissions: ['flows.view', 'executions.view'],
            isAdmin: false
        };
        
        expect(store.hasPermission('flows.view')).toBe(true);
        expect(store.hasPermission('flows.create')).toBe(false);
    });
    
    it('should check if user has role', () => {
        const store = useOAuth2Store();
        store.userInfo = {
            authenticated: true,
            username: 'admin@example.com',
            roles: ['admin'],
            permissions: [],
            isAdmin: true
        };
        
        expect(store.hasRole('admin')).toBe(true);
        expect(store.hasRole('operator')).toBe(false);
        expect(store.isAdmin).toBe(true);
    });
});
```

### Integration Tests

```bash
#!/bin/bash
# Test RBAC with curl

# Admin token
ADMIN_TOKEN="eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."

# Operator token
OPERATOR_TOKEN="eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."

echo "Testing admin access..."
# Should succeed
curl -X POST \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/yaml" \
  --data-binary @test-flow.yml \
  http://localhost:8080/api/v1/flows

echo "Testing operator access..."
# Should fail with 403
curl -X POST \
  -H "Authorization: Bearer $OPERATOR_TOKEN" \
  -H "Content-Type: application/yaml" \
  --data-binary @test-flow.yml \
  http://localhost:8080/api/v1/flows

echo "Testing operator view access..."
# Should succeed
curl -H "Authorization: Bearer $OPERATOR_TOKEN" \
  http://localhost:8080/api/v1/flows
```

---

## Summary

This document provides copy-paste examples for:
- ✅ Protecting backend endpoints with annotations
- ✅ Accessing user info in controllers
- ✅ Custom permission logic
- ✅ Frontend permission checks with composable
- ✅ Declarative permission checks with directives
- ✅ Dynamic menus and conditional rendering
- ✅ Route guards
- ✅ Unit and integration tests

For more information:
- See [README_RBAC.md](README_RBAC.md) for complete documentation
- See [QUICKSTART_RBAC.md](QUICKSTART_RBAC.md) for setup guide
- See [RBAC_IMPLEMENTATION_SUMMARY.md](RBAC_IMPLEMENTATION_SUMMARY.md) for implementation details

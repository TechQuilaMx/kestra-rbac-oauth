# Kestra SSO with OAuth2 RBAC

**Forked from Kestra v1.2.0**

This is an enhanced version of the open-source Kestra orchestration platform with OAuth2 authentication and Role-Based Access Control (RBAC) capabilities.

## Overview

This fork adds enterprise-grade authentication and authorization to Kestra through OAuth2 and role-based access control, enabling secure multi-user deployments with fine-grained permission management.

## New Features

### 1. OAuth2 Authentication

Complete OAuth2/OIDC integration with support for multiple identity providers:
- **Keycloak** - Full support with realm roles and client roles
- **Auth0** - Complete integration
- **Google Workspace** - Support for Google identity provider
- **Azure AD** - Support for Azure Active Directory
- **Any OIDC-compliant provider** - Generic OIDC support with configurable role claim paths

#### Key Features:
- Token refresh with automatic retry queue for expired tokens
- JWT fallback decoding when userinfo endpoint lacks roles
- Multiple role claim extraction strategies
- Configurable role claim paths for different providers
- WWW-Authenticate header suppression for JSON API calls (prevents browser native credential dialogs)

### 2. Role-Based Access Control (RBAC)

Fine-grained permission system with predefined roles:

#### Available Roles:
- **ADMIN** - Full access to all features
- **OPERATOR** - Configurable limited access (view-only + execution by default)

#### Available Permissions (25+):
- **Flows**: view, create, edit, delete
- **Executions**: view, create, restart, kill
- **Templates**: view, create, edit, delete
- **Namespaces**: view, create, edit, delete
- **KV Store**: view, create, edit, delete
- **Secrets**: view, create, edit, delete
- **Admin**: access, stats, triggers
- **Settings**: view, edit

### 3. Backend Authorization

Annotation-based access control on HTTP endpoints:
- `@RequireRole(Role.ADMIN)` - Restrict endpoint to specific roles
- `@RequirePermission(Permission.FLOWS_CREATE)` - Restrict endpoint to specific permissions
- Support for multiple roles/permissions with OR/AND logic
- Class-level and method-level annotations

All endpoints are enforced at the HTTP filter level with proper 403 Forbidden responses.

### 4. Frontend Permission Integration

Vue 3 composable and directives for UX-level permission checks:

#### Composable (`usePermissions`):
```typescript
const { hasPermission, isAdmin, canCreateFlows, canEditFlows } = usePermissions();
```

#### Directives:
- `v-permission="'flows.create'"` - Show element if user has permission
- `v-admin` - Show element if user is admin
- `v-permission-all="['flows.edit', 'flows.delete']"` - Show if user has ALL permissions
- `v-permission-any="['flows.edit', 'flows.delete']"` - Show if user has ANY permission

#### User Info Display:
- User dropdown in UI showing:
  - User name/username/email
  - Assigned roles
  - Logout option
- Session persistence across page refreshes

### 5. New Endpoints

- `GET /api/v1/user/me` - Returns current user info, roles, and permissions
- `POST /api/v1/oauth2/login` - Initiates OAuth2 login flow
- `GET /api/v1/oauth2/callback` - Handles OAuth2 callback
- `POST /api/v1/oauth2/logout` - Performs OAuth2 logout
- `POST /api/v1/oauth2/refresh` - Refreshes access token

## Implementation Details

### Backend Components (Java/Micronaut)

**New Classes:**
- `OAuth2Configuration.java` - OAuth2 configuration properties
- `AuthorizationConfiguration.java` - Role-permission mapping configuration
- `Role.java` - Enum of available roles
- `Permission.java` - Enum of available permissions
- `UserInfo.java` - User information with roles and permissions
- `@RequireRole` - Annotation for role-based access control
- `@RequirePermission` - Annotation for permission-based access control
- `AuthorizationFilter.java` - HTTP filter enforcing role/permission requirements
- `OAuth2Service.java` - Enhanced with role extraction from multiple claim structures
- `UserController.java` - Endpoint for user information

**Modified Classes:**
- `AuthenticationFilter.java` - Added Accept header check for WWW-Authenticate suppression
- `FlowController.java` - Added permission annotations to create/update/delete endpoints

### Frontend Components (Vue 3/TypeScript)

**New Files:**
- `ui/src/composables/usePermissions.ts` - Permission checking composable
- `ui/src/directives/permissions.ts` - Permission-based visibility directives
- `ui/src/components/Auth.vue` - User info dropdown with logout

**Modified Files:**
- `ui/src/stores/oauth2.ts` - Added user info state and fetching
- `ui/src/override/stores/auth.ts` - Integrated with OAuth2 RBAC system
- `ui/src/main.js` - Registered permission directives

### Tests

**New Test Classes:**
- `OAuth2ServiceTest.java` - 7 unit tests covering:
  - Role extraction from Keycloak realm_access
  - Role extraction from Keycloak resource_access (client roles)
  - Role extraction from generic roles claim
  - Role extraction from custom claim paths
  - Empty roles handling
  - Multiple role sources deduplication
  - JWT payload decoding without signature verification

**Modified Tests:**
- `AuthenticationFilterTest.java` - Updated to verify WWW-Authenticate header suppression for JSON API calls

## Configuration

### Backend (application-override.yml)

```yaml
kestra:
  server:
    oauth2:
      enabled: true
      provider: keycloak
      client-id: YOUR_CLIENT_ID
      client-secret: YOUR_CLIENT_SECRET
      authorization-endpoint: http://localhost:8085/realms/master/protocol/openid-connect/auth
      token-endpoint: http://localhost:8085/realms/master/protocol/openid-connect/token
      user-info-endpoint: http://localhost:8085/realms/master/protocol/openid-connect/userinfo
      logout-endpoint: http://localhost:8085/realms/master/protocol/openid-connect/logout
      jwks-endpoint: http://localhost:8085/realms/master/protocol/openid-connect/certs
      scope: openid profile email
    authorization:
      operator-permissions:
        - flows.view
        - executions.view
        - executions.create
        - templates.view
```

### Frontend (.env.development.local)

```
VITE_OAUTH2_ENABLED=true
VITE_OAUTH2_LOGIN_URL=/api/v1/oauth2/login
VITE_OAUTH2_CALLBACK_URL=/ui/oauth2-callback
```

## Getting Started

### Prerequisites
- Keycloak (or other OAuth2/OIDC provider) running and configured
- Java 21+
- Node.js 22+

### Quick Start

1. **Set up Identity Provider (Keycloak)**
   - Create realm and OAuth2 client
   - Create `kestra-admin` and `kestra-operator` roles
   - Assign roles to test users

2. **Configure Backend**
   ```bash
   cp cli/src/main/resources/application-override.example.yml \
      cli/src/main/resources/application-override.yml
   # Edit with your OAuth2 provider details
   ```

3. **Start Backend**
   ```bash
   ./gradlew runStandalone
   ```

4. **Start Frontend**
   ```bash
   cd ui && npm run dev
   ```

5. **Test OAuth2 Login**
   - Open http://localhost:5173
   - Click "Sign in with OAuth2"
   - Login with your test user
   - Verify roles appear in user dropdown

## Security Features

- **Token Validation**: All tokens validated against JWKS endpoint
- **Backend Enforcement**: All authorization checks enforced at HTTP filter level
- **Frontend UX Only**: Frontend permission checks are for UX improvements only, not security boundaries
- **Session Management**: Secure token storage in sessionStorage with automatic refresh
- **Stateless**: All user info comes from OAuth2 token (no session state)
- **Defense in Depth**: Multiple validation layers (authentication → authorization → endpoint logic)

## Changes from Original Kestra

| Feature | Status | Description |
|---------|--------|-------------|
| OAuth2 Authentication | ✅ Added | Full OAuth2/OIDC support with token refresh |
| Role-Based Access Control | ✅ Added | ADMIN and OPERATOR roles with 25+ permissions |
| Permission Annotations | ✅ Added | `@RequireRole` and `@RequirePermission` on endpoints |
| Authorization Filter | ✅ Added | HTTP filter enforcing role/permission requirements |
| User Info Endpoint | ✅ Added | `/api/v1/user/me` returns user roles and permissions |
| Permission Composable | ✅ Added | Vue composable for frontend permission checks |
| Permission Directives | ✅ Added | `v-permission`, `v-admin` directives for UX |
| User Dropdown | ✅ Added | Displays user info and logout option |
| Multi-Provider Support | ✅ Added | Support for Keycloak, Auth0, Google, Azure AD, generic OIDC |
| Token Refresh | ✅ Added | Automatic token refresh with request queueing |
| JWT Fallback | ✅ Added | JWT payload parsing when userinfo lacks roles |
| BasicAuth | ✅ Preserved | Still supported when OAuth2 disabled |

## Testing

### Run All Tests
```bash
./gradlew test
```

### Run OAuth2 Tests Only
```bash
./gradlew :webserver:test --tests OAuth2ServiceTest
```

### Run UI Tests
```bash
cd ui && npm run test
```

## Documentation

For detailed information on specific features:
- **OAuth2 Setup**: See `BACKEND_OAUTH2_IMPLEMENTATION.md`
- **RBAC Architecture**: See code comments in `OAuth2Service.java`, `AuthorizationFilter.java`
- **Frontend Usage**: See `usePermissions()` composable and permission directives

## Support and Contribution

This is a community fork. For issues specific to this OAuth2/RBAC implementation:
- Check existing code for implementation patterns
- Review test cases for expected behavior
- Enable debug logging in `AuthorizationFilter` and `OAuth2Service`

For issues with the original Kestra platform, visit: https://github.com/kestra-io/kestra

## License

Same as original Kestra project (Apache 2.0)

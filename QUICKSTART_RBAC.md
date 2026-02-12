# Quick Start: Setting Up RBAC with Keycloak

This guide will help you set up role-based access control with Keycloak in under 10 minutes.

## Prerequisites

- Keycloak running on port 8085
- Kestra backend configured with OAuth2 (see README_OAUTH2.md)
- Admin access to Keycloak

## Step 1: Create Realm Roles

1. Login to Keycloak Admin Console: http://localhost:8085
2. Select your realm (e.g., "master")
3. Go to **Realm Roles** in the left menu
4. Click **Create role**:
   - **Role name**: `kestra-admin`
   - **Description**: Full access to Kestra
   - Click **Save**

5. Click **Create role** again:
   - **Role name**: `kestra-operator`
   - **Description**: Limited access to Kestra
   - Click **Save**

## Step 2: Configure Client to Include Roles in Token

1. Go to **Clients** → Select `kestra-app`
2. Click on **Client scopes** tab
3. Click on `kestra-app-dedicated` (or the default client scope)
4. Click **Add mapper** → **By configuration** → **User Realm Role**
5. Configure the mapper:
   - **Name**: `realm-roles`
   - **Token Claim Name**: `realm_access.roles`
   - **Claim JSON Type**: `String`
   - **Add to ID token**: ON
   - **Add to access token**: ON
   - **Add to userinfo**: ON
   - Click **Save**

## Step 3: Create Test Users

### Admin User

1. Go to **Users** → Click **Add user**
2. Fill in:
   - **Username**: `admin@example.com`
   - **Email**: `admin@example.com`
   - **First name**: `Admin`
   - **Last name**: `User`
   - **Email verified**: ON
   - Click **Create**

3. Go to **Credentials** tab:
   - Click **Set password**
   - **Password**: `admin123`
   - **Temporary**: OFF
   - Click **Save**

4. Go to **Role mappings** tab:
   - Click **Assign role**
   - Filter by "Realm roles"
   - Select `kestra-admin`
   - Click **Assign**

### Operator User

1. Go to **Users** → Click **Add user**
2. Fill in:
   - **Username**: `operator@example.com`
   - **Email**: `operator@example.com`
   - **First name**: `Operator`
   - **Last name**: `User`
   - **Email verified**: ON
   - Click **Create**

3. Go to **Credentials** tab:
   - Click **Set password**
   - **Password**: `operator123`
   - **Temporary**: OFF
   - Click **Save**

4. Go to **Role mappings** tab:
   - Click **Assign role**
   - Filter by "Realm roles"
   - Select `kestra-operator`
   - Click **Assign**

## Step 4: Configure Kestra

Update `cli/src/main/resources/application-override.yml`:

```yaml
kestra:
  server:
    oauth2:
      enabled: true
      provider: keycloak
      client-id: kestra-app
      client-secret: YOUR_CLIENT_SECRET
      authorization-endpoint: http://localhost:8085/realms/master/protocol/openid-connect/auth
      token-endpoint: http://localhost:8085/realms/master/protocol/openid-connect/token
      user-info-endpoint: http://localhost:8085/realms/master/protocol/openid-connect/userinfo
      logout-endpoint: http://localhost:8085/realms/master/protocol/openid-connect/logout
      jwks-endpoint: http://localhost:8085/realms/master/protocol/openid-connect/certs
      scope: openid profile email
      issuer: http://localhost:8085/realms/master
      audience: kestra-app
      open-urls:
        - /api/v1/oauth2
    authorization:
      # Configure operator permissions
      operator-permissions:
        - flows.view
        - executions.view
        - executions.create
        - templates.view
        - namespaces.view
        - kv.view
        - settings.view
```

## Step 5: Start Kestra

```bash
# Start backend
./gradlew runStandalone

# In another terminal, start frontend
cd ui
npm run dev
```

## Step 6: Test the Roles

### Test Admin User

1. Open browser: http://localhost:5173
2. Click "Sign in with Keycloak"
3. Login with:
   - Username: `admin@example.com`
   - Password: `admin123`
4. You should be redirected to Kestra dashboard
5. Open browser console and check user info:
   ```javascript
   // In browser console
   fetch('/api/v1/user/me', {
     headers: {
       'Authorization': 'Bearer ' + sessionStorage.getItem('oauth2_tokens')?.access_token
     }
   }).then(r => r.json()).then(console.log)
   ```
   
   Expected output:
   ```json
   {
     "authenticated": true,
     "username": "admin@example.com",
     "roles": ["admin"],
     "permissions": [...all permissions...],
     "isAdmin": true
   }
   ```

6. Test admin capabilities:
   - ✅ Can see "Create Flow" button
   - ✅ Can create new flows
   - ✅ Can edit flows
   - ✅ Can delete flows
   - ✅ Can access all features

### Test Operator User

1. Logout (if logged in)
2. Login with:
   - Username: `operator@example.com`
   - Password: `operator123`
3. Check user info (same as above)
   
   Expected output:
   ```json
   {
     "authenticated": true,
     "username": "operator@example.com",
     "roles": ["operator"],
     "permissions": [
       "flows.view",
       "executions.view",
       "executions.create",
       "templates.view",
       "namespaces.view",
       "kv.view",
       "settings.view"
     ],
     "isAdmin": false
   }
   ```

4. Test operator limitations:
   - ❌ "Create Flow" button should be hidden (no flows.create permission)
   - ❌ "Delete" buttons should be hidden (no flows.delete permission)
   - ❌ "Edit Flow" options should be hidden (no flows.edit permission)
   - ✅ Can view flows
   - ✅ Can view and trigger executions
   - ✅ Can view templates

### Test API Authorization

Test with curl or Postman:

```bash
# Get operator access token first (login and copy from browser console)
OPERATOR_TOKEN="eyJhbGciOiJS..."

# This should work (operators can view flows)
curl -H "Authorization: Bearer $OPERATOR_TOKEN" \
     http://localhost:8080/api/v1/flows

# This should fail with 403 Forbidden (operators cannot create flows)
curl -X POST \
     -H "Authorization: Bearer $OPERATOR_TOKEN" \
     -H "Content-Type: application/yaml" \
     --data-binary @my-flow.yml \
     http://localhost:8080/api/v1/flows
```

Expected response for unauthorized request:
```json
{
  "error": "Insufficient permissions"
}
```

## Step 7: Customize Permissions

To give operators more permissions, edit `application-override.yml`:

```yaml
kestra:
  server:
    authorization:
      operator-permissions:
        - flows.view
        - flows.create      # ← Add this to allow creating flows
        - flows.edit        # ← Add this to allow editing flows
        - executions.view
        - executions.create
        - executions.restart # ← Add this to allow restarting executions
        - templates.view
        - namespaces.view
        - kv.view
        - kv.create         # ← Add this to allow creating KV entries
        - settings.view
```

Restart Kestra backend and test again.

## Troubleshooting

### Roles Not Showing in Token

**Check token claims**:
1. Go to Keycloak Admin Console
2. **Clients** → `kestra-app` → **Client scopes** tab
3. Click on `kestra-app-dedicated`
4. Go to **Evaluate** tab
5. Select user: `admin@example.com`
6. Click **Generated access token**
7. Look for:
   ```json
   {
     "realm_access": {
       "roles": ["kestra-admin"]
     }
   }
   ```

If roles are missing:
- Verify the mapper is configured correctly (Step 2)
- Verify user has role assigned (Step 3)
- Try logging out and logging back in

### Backend Not Extracting Roles

Enable debug logging in `application-override.yml`:

```yaml
logger:
  levels:
    io.kestra.webserver.services.OAuth2Service: DEBUG
    io.kestra.webserver.filter.AuthorizationFilter: DEBUG
```

Check logs for:
```
Extracted roles for user admin@example.com: [ADMIN]
```

### 403 Forbidden Even for Admin

Check:
1. User has `kestra-admin` role in Keycloak
2. Token contains roles (use jwt.io to decode)
3. Backend logs show role extraction
4. `/api/v1/user/me` returns `isAdmin: true`

## Next Steps

- Read [README_RBAC.md](README_RBAC.md) for detailed RBAC documentation
- Add more custom roles (DEVELOPER, VIEWER, etc.)
- Configure permissions per environment (dev, staging, prod)
- Set up SSO with your company's identity provider

## Quick Reference

### Default Permissions

**ADMIN Role** (automatic):
- All permissions

**OPERATOR Role** (configurable):
- Default: view-only + execute
- Configurable via `kestra.server.authorization.operator-permissions`

### API Endpoints

- `GET /api/v1/user/me` - Get current user info, roles, and permissions
- All protected endpoints check permissions via `@RequirePermission` annotation

### Frontend Usage

```vue
<script setup>
import { usePermissions } from '@/composables/usePermissions';
const { canCreateFlows, isAdmin } = usePermissions();
</script>

<template>
  <button v-if="canCreateFlows">Create Flow</button>
  <div v-permission="'flows.delete'">Delete option</div>
  <div v-admin>Admin panel</div>
</template>
```

That's it! You now have a working RBAC system with admin and operator roles.

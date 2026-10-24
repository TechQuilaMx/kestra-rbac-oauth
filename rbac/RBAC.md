# Kestra OAuth2 Authentication Implementation

This document describes the OAuth2/OIDC authentication implementation added to Kestra, enabling integration with identity providers like Keycloak, Auth0, Google, GitHub, and other OIDC-compliant providers.

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Changes from Original Project](#changes-from-original-project)
- [OAuth2 Flow](#oauth2-flow)
- [Configuration](#configuration)
- [Integration with OAuth2 Providers](#integration-with-oauth2-providers)
- [Security Considerations](#security-considerations)
- [Development Setup](#development-setup)
- [Production Deployment](#production-deployment)

## Overview

This implementation adds enterprise-grade OAuth2/OIDC authentication to Kestra, allowing organizations to:

- Use existing identity providers (Keycloak, Auth0, Okta, Azure AD, Google, etc.)
- Implement Single Sign-On (SSO) across applications
- Centralize user management and access control
- Support standard OIDC flows with PKCE
- Maintain secure token handling with server-side secrets

**Key Features:**
- ✅ Full OAuth2 Authorization Code Flow with PKCE
- ✅ Secure server-side token exchange (no client secrets in frontend)
- ✅ Automatic token refresh handling
- ✅ CORS-safe architecture
- ✅ Provider-agnostic implementation (works with any OIDC provider)
- ✅ Backward compatible with existing BasicAuth

## Architecture

### System Components

```
┌─────────────────┐         ┌──────────────────┐         ┌─────────────────┐
│                 │         │                  │         │                 │
│  Vue.js Frontend│◄───────►│  Kestra Backend  │◄───────►│  OAuth2 Provider│
│   (Port 5173)   │         │   (Port 8080)    │         │   (Keycloak)    │
│                 │         │                  │         │                 │
└─────────────────┘         └──────────────────┘         └─────────────────┘
        │                            │                            │
        │ 1. Redirect to provider    │                            │
        ├────────────────────────────┼───────────────────────────►│
        │                            │                            │
        │ 2. User logs in            │                            │
        │◄────────────────────────────────────────────────────────┤
        │                            │                            │
        │ 3. Callback with auth code │                            │
        ├────────────────────────────►                            │
        │                            │ 4. Exchange code for tokens│
        │                            ├───────────────────────────►│
        │                            │                            │
        │                            │◄───────────────────────────┤
        │ 5. Return tokens           │                            │
        │◄───────────────────────────┤                            │
        │                            │                            │
        │ 6. API calls with Bearer   │                            │
        ├────────────────────────────►                            │
        │                            │ 7. Validate tokens         │
        │                            ├───────────────────────────►│
```

### Backend Components

**New Java Classes:**

1. **OAuth2Configuration.java** (`webserver/src/main/java/io/kestra/webserver/configurations/`)
   - Configuration properties for OAuth2 setup
   - Reads from `kestra.server.oauth2.*` properties
   - Supports all standard OIDC endpoints

2. **OAuth2Service.java** (`webserver/src/main/java/io/kestra/webserver/services/`)
   - Token validation via userinfo endpoint
   - User information extraction
   - Token introspection support

3. **OAuth2TokenValidator.java** (`webserver/src/main/java/io/kestra/webserver/validators/`)
   - JWT signature validation using JWKS
   - Issuer and audience validation
   - Expiration checks

4. **OAuth2Controller.java** (`webserver/src/main/java/io/kestra/webserver/controllers/api/`)
   - `POST /api/v1/oauth2/token` - Authorization code exchange
   - `POST /api/v1/oauth2/refresh` - Token refresh
   - Secure server-side token operations

5. **AuthenticationFilter.java** (Modified)
   - OAuth2 Bearer token authentication
   - Falls back to BasicAuth if OAuth2 not configured
   - Checks OAuth2 `openUrls` for public endpoints

### Frontend Components

**New Vue Components:**

1. **OAuth2Login.vue** (`ui/src/components/basicauth/`)
   - OAuth2 login page with provider redirect button
   - Initializes OAuth2 flow

2. **OAuth2Callback.vue** (`ui/src/components/basicauth/`)
   - Handles OAuth2 provider redirect
   - Processes authorization code
   - Displays loading/success/error states

**New Utilities:**

3. **oauth2.ts** (`ui/src/utils/`)
   - `OAuth2Manager` class implementing OAuth2 flow
   - Token management and storage
   - PKCE support with state/nonce validation

4. **oauth2.ts Store** (`ui/src/stores/`)
   - Pinia store for OAuth2 state management
   - Token persistence
   - Authentication status tracking

**Modified Files:**

5. **axios.ts** - Added Bearer token injection and refresh interceptors
6. **routes.js** - Added OAuth2 routes (`/ui/login`, `/ui/oauth2-callback`)
7. **main.js** - Added OAuth2 authentication guards

## Changes from Original Project

### Backend Changes

#### New Files
- `webserver/src/main/java/io/kestra/webserver/configurations/OAuth2Configuration.java`
- `webserver/src/main/java/io/kestra/webserver/services/OAuth2Service.java`
- `webserver/src/main/java/io/kestra/webserver/validators/OAuth2TokenValidator.java`
- `webserver/src/main/java/io/kestra/webserver/controllers/api/OAuth2Controller.java`

#### Modified Files
- `webserver/src/main/java/io/kestra/webserver/filter/AuthenticationFilter.java`
  - Added OAuth2 Bearer token authentication
  - Added OAuth2 openUrls support
  
- `webserver/src/main/java/io/kestra/webserver/controllers/api/MiscController.java`
  - Added OAuth2 configuration exposure to frontend
  - New fields: `oauth2ClientId`, `oauth2AuthEndpoint`, `oauth2TokenEndpoint`, etc.

- `webserver/build.gradle`
  - Added dependency: `com.nimbusds:nimbus-jose-jwt:9.37.3`

#### Configuration
- `cli/src/main/resources/application-override.yml`
  - New section: `kestra.server.oauth2.*`

### Frontend Changes

#### New Files
- `ui/src/components/basicauth/OAuth2Login.vue`
- `ui/src/components/basicauth/OAuth2Callback.vue`
- `ui/src/utils/oauth2.ts`
- `ui/src/stores/oauth2.ts`
- `ui/src/composables/useOAuth2.ts`

#### Modified Files
- `ui/src/utils/axios.ts`
  - Bearer token injection in request interceptor
  - Token refresh on 401 errors
  
- `ui/src/routes/routes.js`
  - Added OAuth2 login route: `/ui/login`
  - Added OAuth2 callback route: `/ui/oauth2-callback`
  - Renamed BasicAuth login to `/basicauth-login`

- `ui/src/main.js`
  - OAuth2 initialization in router guard
  - Authentication checks with OAuth2 priority
  - Token-based access control

## OAuth2 Flow

### 1. Initial Access

```javascript
// User visits http://localhost:5173
// Router guard checks authentication
// No OAuth2 tokens found → redirect to /ui/login
```

### 2. Provider Redirect

```javascript
// User clicks "Login with Keycloak"
// OAuth2Manager.redirectToLogin() called
// Generate state & nonce for CSRF protection
// Redirect to: https://keycloak.example.com/auth?
//   client_id=kestra-app&
//   redirect_uri=http://localhost:5173/ui/oauth2-callback&
//   response_type=code&
//   scope=openid profile email&
//   state={random}&
//   nonce={random}
```

### 3. Provider Authentication

```
User authenticates at OAuth2 provider
Provider redirects back to callback URL with authorization code
```

### 4. Authorization Code Exchange (Backend)

```javascript
// Frontend: POST /api/v1/oauth2/token
// Body: { code, redirectUri }

// Backend exchanges code for tokens with provider:
// POST https://keycloak.example.com/token
// Body: grant_type=authorization_code&
//       code={code}&
//       client_id=kestra-app&
//       client_secret={secret}&  // ← Server-side only!
//       redirect_uri={uri}

// Returns: { access_token, refresh_token, id_token, expires_in }
```

### 5. Token Storage and Usage

```javascript
// Frontend stores tokens in sessionStorage
// Sets isAuthenticated = true
// Redirects to home page

// All API requests include:
// Authorization: Bearer {access_token}
```

### 6. Token Refresh

```javascript
// When access token expires (detected on 401 error)
// Frontend: POST /api/v1/oauth2/refresh
// Body: { refreshToken }

// Backend calls provider refresh endpoint
// Returns new access token
// Frontend updates stored tokens
// Retries failed request
```

## Configuration

### Backend Configuration

Add to `cli/src/main/resources/application-override.yml`:

```yaml
kestra:
  server:
    oauth2:
      enabled: true
      provider: keycloak  # or auth0, google, github, etc.
      client-id: kestra-app
      client-secret: your-client-secret
      authorization-endpoint: http://localhost:8085/realms/master/protocol/openid-connect/auth
      token-endpoint: http://localhost:8085/realms/master/protocol/openid-connect/token
      user-info-endpoint: http://localhost:8085/realms/master/protocol/openid-connect/userinfo
      logout-endpoint: http://localhost:8085/realms/master/protocol/openid-connect/logout
      jwks-endpoint: http://localhost:8085/realms/master/protocol/openid-connect/certs
      scope: openid profile email
      issuer: http://localhost:8085/realms/master
      audience: kestra-app
      enable-introspection: false
      open-urls:
        - /api/v1/oauth2  # Public endpoints (token exchange, refresh)
```

### Environment Variables

All configuration can be overridden with environment variables:

```bash
OAUTH2_ENABLED=true
OAUTH2_PROVIDER=keycloak
OAUTH2_CLIENT_ID=kestra-app
OAUTH2_CLIENT_SECRET=your-secret
OAUTH2_AUTH_ENDPOINT=http://keycloak:8085/realms/master/protocol/openid-connect/auth
OAUTH2_TOKEN_ENDPOINT=http://keycloak:8085/realms/master/protocol/openid-connect/token
OAUTH2_USER_INFO_ENDPOINT=http://keycloak:8085/realms/master/protocol/openid-connect/userinfo
OAUTH2_LOGOUT_ENDPOINT=http://keycloak:8085/realms/master/protocol/openid-connect/logout
OAUTH2_JWKS_ENDPOINT=http://keycloak:8085/realms/master/protocol/openid-connect/certs
OAUTH2_SCOPE=openid profile email
OAUTH2_ISSUER=http://keycloak:8085/realms/master
OAUTH2_AUDIENCE=kestra-app
```

## Integration with OAuth2 Providers

### Keycloak Setup

1. **Start Keycloak:**
```bash
docker run -p 8085:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:latest start-dev
```

2. **Create Realm:**
   - Access Keycloak Admin Console: http://localhost:8085
   - Create or use existing realm (e.g., `master`)

3. **Create Client:**
   - Clients → Create Client
   - Client ID: `kestra-app`
   - Client Type: `OpenID Connect`
   - Valid Redirect URIs: `http://localhost:5173/ui/oauth2-callback`
   - Web Origins: `http://localhost:5173`

4. **Configure Client:**
   - Access Type: `confidential`
   - Standard Flow Enabled: `ON`
   - Direct Access Grants Enabled: `ON`
   - Copy Client Secret from Credentials tab

5. **Create Scopes:**
   - Client Scopes → Create
   - Add `openid`, `profile`, `email` scopes
   - Assign to `kestra-app` client

6. **Create User:**
   - Users → Add User
   - Username: `kestra-admin`
   - Email: `admin@kestra.io`
   - Set Password (Credentials tab)

### Auth0 Setup

1. **Create Application:**
   - Applications → Create Application
   - Name: `Kestra`
   - Type: `Single Page Application`

2. **Configure Application:**
   - Allowed Callback URLs: `http://localhost:5173/ui/oauth2-callback`
   - Allowed Web Origins: `http://localhost:5173`
   - Allowed Logout URLs: `http://localhost:5173/ui/login`

3. **Get Endpoints:**
   - Settings → Advanced Settings → Endpoints
   - Copy OAuth Authorization URL, OAuth Token URL, etc.

4. **Configuration:**
```yaml
kestra:
  server:
    oauth2:
      enabled: true
      provider: auth0
      client-id: your-auth0-client-id
      client-secret: your-auth0-client-secret
      authorization-endpoint: https://your-tenant.auth0.com/authorize
      token-endpoint: https://your-tenant.auth0.com/oauth/token
      user-info-endpoint: https://your-tenant.auth0.com/userinfo
      logout-endpoint: https://your-tenant.auth0.com/v2/logout
      scope: openid profile email
      issuer: https://your-tenant.auth0.com/
      audience: your-auth0-client-id
```

### Google OAuth Setup

1. **Create OAuth Client:**
   - Google Cloud Console → APIs & Services → Credentials
   - Create OAuth 2.0 Client ID
   - Application type: `Web application`
   - Authorized redirect URIs: `http://localhost:5173/ui/oauth2-callback`

2. **Configuration:**
```yaml
kestra:
  server:
    oauth2:
      enabled: true
      provider: google
      client-id: your-google-client-id.apps.googleusercontent.com
      client-secret: your-google-client-secret
      authorization-endpoint: https://accounts.google.com/o/oauth2/v2/auth
      token-endpoint: https://oauth2.googleapis.com/token
      user-info-endpoint: https://openidconnect.googleapis.com/v1/userinfo
      logout-endpoint: https://accounts.google.com/logout
      jwks-endpoint: https://www.googleapis.com/oauth2/v3/certs
      scope: openid profile email
      issuer: https://accounts.google.com
```

### GitHub OAuth Setup

1. **Create OAuth App:**
   - Settings → Developer settings → OAuth Apps → New OAuth App
   - Authorization callback URL: `http://localhost:5173/ui/oauth2-callback`

2. **Configuration:**
```yaml
kestra:
  server:
    oauth2:
      enabled: true
      provider: github
      client-id: your-github-client-id
      client-secret: your-github-client-secret
      authorization-endpoint: https://github.com/login/oauth/authorize
      token-endpoint: https://github.com/login/oauth/access_token
      user-info-endpoint: https://api.github.com/user
      scope: read:user user:email
```

## Security Considerations

### Implemented Security Features

1. **Server-Side Token Exchange**
   - Client secret never exposed to frontend
   - Authorization code exchange happens on backend
   - Prevents secret theft from browser

2. **CSRF Protection**
   - State parameter validation
   - Nonce parameter for replay attack prevention
   - Secure random generation

3. **Token Security**
   - Tokens stored in sessionStorage (cleared on browser close)
   - JWT signature validation with JWKS
   - Token expiration checks
   - Automatic token refresh

4. **CORS Safety**
   - All OAuth2 endpoints handled by backend
   - No direct calls from frontend to identity provider
   - Proper CORS configuration

5. **Authentication Filter**
   - Bearer token validation on all API requests
   - OAuth2 openUrls for public endpoints
   - Falls back to BasicAuth if OAuth2 not configured

### Production Recommendations

1. **Use HTTPS:**
```yaml
kestra:
  server:
    oauth2:
      authorization-endpoint: https://keycloak.example.com/auth
      token-endpoint: https://keycloak.example.com/token
      # ... all endpoints should use HTTPS
```

2. **Secrets Management:**
   - Use environment variables for client secrets
   - Use secrets management systems (HashiCorp Vault, AWS Secrets Manager)
   - Never commit secrets to version control

3. **Token Storage:**
   - Consider httpOnly cookies instead of sessionStorage
   - Implement token rotation
   - Set appropriate token expiration times

4. **Network Security:**
   - Use internal network for backend-to-provider communication
   - Implement rate limiting on OAuth2 endpoints
   - Monitor for suspicious authentication patterns

## Development Setup

### Prerequisites

- Java 21+
- Node.js 22+
- Docker & Docker Compose
- Gradle (wrapper included)

### Quick Start

1. **Start Keycloak:**
```bash
docker run -d -p 8085:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  --name keycloak \
  quay.io/keycloak/keycloak:latest start-dev
```

2. **Configure Keycloak:**
   - Create realm and client as described in [Keycloak Setup](#keycloak-setup)

3. **Start Backend:**
```bash
./gradlew runStandalone
# Backend runs on http://localhost:8080
```

4. **Start Frontend:**
```bash
cd ui
npm install
npm run dev
# Frontend runs on http://localhost:5173
```

5. **Test OAuth2 Flow:**
   - Navigate to http://localhost:5173
   - Should redirect to /ui/login
   - Click "Login with Keycloak"
   - Authenticate with Keycloak user
   - Should redirect back and access Kestra dashboard

### Debugging

**Backend Logs:**
```bash
# Enable debug logging for OAuth2
# Add to application-override.yml:
logger:
  levels:
    io.kestra.webserver.controllers.api.OAuth2Controller: DEBUG
    io.kestra.webserver.services.OAuth2Service: DEBUG
```

**Frontend Logs:**
- Open browser DevTools → Console
- OAuth2 flow logs show authentication status
- Check sessionStorage for tokens: `oauth2_tokens`

**Common Issues:**

1. **401 Unauthorized on token exchange:**
   - Verify client secret matches Keycloak
   - Check redirect URI matches exactly
   - Ensure all required scopes are assigned to client

2. **CORS errors:**
   - Should not occur with this implementation (backend handles all OAuth2 calls)
   - If you see CORS errors, check that frontend is calling backend, not provider directly

3. **Redirect loop:**
   - Clear browser sessionStorage
   - Check OAuth2 initialization in router guard
   - Verify tokens are being persisted correctly

## Production Deployment

### Docker Deployment

Use the provided `Dockerfile.rbac`:

```bash
docker build -f Dockerfile.rbac -t kestra-sso:latest .

docker run -p 8080:8080 \
  -e OAUTH2_ENABLED=true \
  -e OAUTH2_CLIENT_ID=kestra-app \
  -e OAUTH2_CLIENT_SECRET=your-secret \
  -e OAUTH2_AUTH_ENDPOINT=https://keycloak.example.com/realms/master/protocol/openid-connect/auth \
  -e OAUTH2_TOKEN_ENDPOINT=https://keycloak.example.com/realms/master/protocol/openid-connect/token \
  -e OAUTH2_USER_INFO_ENDPOINT=https://keycloak.example.com/realms/master/protocol/openid-connect/userinfo \
  -e OAUTH2_LOGOUT_ENDPOINT=https://keycloak.example.com/realms/master/protocol/openid-connect/logout \
  -e OAUTH2_JWKS_ENDPOINT=https://keycloak.example.com/realms/master/protocol/openid-connect/certs \
  -e OAUTH2_SCOPE="openid profile email" \
  -e OAUTH2_ISSUER=https://keycloak.example.com/realms/master \
  -e OAUTH2_AUDIENCE=kestra-app \
  kestra-sso:latest server standalone
```

### Docker Compose

```yaml
version: "3.9"

services:
  keycloak:
    image: quay.io/keycloak/keycloak:latest
    command: start-dev
    ports:
      - "8085:8080"
    environment:
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin
    
  kestra:
    image: kestra-sso:latest
    ports:
      - "8080:8080"
    environment:
      KESTRA_CONFIGURATION: |
        kestra:
          repository:
            type: postgres
          storage:
            type: local
          queue:
            type: postgres
          server:
            oauth2:
              enabled: true
              provider: keycloak
              client-id: kestra-app
              client-secret: your-secret
              authorization-endpoint: http://keycloak:8080/realms/master/protocol/openid-connect/auth
              token-endpoint: http://keycloak:8080/realms/master/protocol/openid-connect/token
              user-info-endpoint: http://keycloak:8080/realms/master/protocol/openid-connect/userinfo
              logout-endpoint: http://keycloak:8080/realms/master/protocol/openid-connect/logout
              jwks-endpoint: http://keycloak:8080/realms/master/protocol/openid-connect/certs
              scope: openid profile email
              issuer: http://keycloak:8080/realms/master
              audience: kestra-app
              open-urls:
                - /api/v1/oauth2
        datasources:
          postgres:
            url: jdbc:postgresql://postgres:5432/kestra
            username: kestra
            password: k3str4
    depends_on:
      - keycloak
      - postgres
      
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: kestra
      POSTGRES_USER: kestra
      POSTGRES_PASSWORD: k3str4
    volumes:
      - postgres-data:/var/lib/postgresql/data

volumes:
  postgres-data:
```

### Kubernetes Deployment

See `k8s/` directory for Kubernetes manifests with OAuth2 configuration via ConfigMaps and Secrets.

## Testing

### Manual Testing

1. Clear browser storage
2. Navigate to http://localhost:5173
3. Should redirect to /ui/login
4. Click "Login with [Provider]"
5. Authenticate at provider
6. Should redirect to /ui/oauth2-callback
7. Should show "Sign-In Successful"
8. Should redirect to home dashboard
9. All API calls should include Bearer token
10. Try accessing protected routes - should work
11. Logout and verify redirect back to login

### Automated Testing

```bash
# Backend tests
./gradlew test

# Frontend tests
cd ui
npm run test
```

## Contributing

When contributing OAuth2-related changes:

1. Maintain backward compatibility with BasicAuth
2. Add tests for new OAuth2 functionality
3. Update this README with configuration changes
4. Follow the existing code style
5. Ensure CORS compliance (backend-only OAuth2 calls)
6. Keep client secrets server-side only

## License

Same as original Kestra project - Apache 2.0 License

## Support

For issues or questions:
- Open a GitHub issue
- Check Kestra Slack community
- Refer to main Kestra documentation: https://kestra.io/docs

---

**Last Updated:** January 30, 2026
**Kestra Version:** 1.2.0-SNAPSHOT
**OAuth2 Implementation Version:** 1.0

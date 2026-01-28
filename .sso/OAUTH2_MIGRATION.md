# OAuth2 Authentication Migration Guide - Kestra SSO

This guide explains how to migrate from Basic Auth to OAuth2 (Keycloak) authentication while maintaining flexibility for other OAuth2 providers.

## Current Architecture vs. OAuth2

### Current (Basic Auth)
```
User enters credentials → BasicAuth.login(user, pass) → JWT token stored
→ Token sent in every request → App accessible
```

### New (OAuth2)
```
User clicks login → Redirect to OAuth2 Provider (Keycloak) → 
User logs in at provider → Redirect back with auth code → 
Exchange code for tokens → Store tokens → App accessible
```

## Key Differences

| Aspect | Basic Auth | OAuth2 |
|--------|-----------|--------|
| **Credential handling** | App handles passwords | Provider handles passwords |
| **Tokens** | JWT only | Access token + Refresh token + ID token |
| **Token storage** | localStorage | sessionStorage (httpOnly cookies preferred) |
| **Session management** | Manual | Provider manages session |
| **Logout** | Clear localStorage | Redirect to provider logout |
| **Multi-provider** | Single backend | Any OIDC-compliant provider |

---

## Implementation Plan

### Phase 1: Create OAuth2 Service Layer

**File**: `ui/src/utils/oauth2.ts` (NEW)

```typescript
// OAuth2 configuration interface (flexible for any provider)
interface OAuth2Config {
    clientId: string;
    redirectUri: string;
    authorizationEndpoint: string;      // e.g., https://keycloak.com/auth
    tokenEndpoint: string;               // e.g., https://keycloak.com/token
    userInfoEndpoint: string;            // e.g., https://keycloak.com/userinfo
    logoutEndpoint: string;              // e.g., https://keycloak.com/logout
    scope: string;                       // e.g., "openid profile email"
    responseType: string;                // usually "code"
    grantType: string;                   // usually "authorization_code"
}

interface Tokens {
    accessToken: string;
    refreshToken: string;
    idToken: string;
    expiresIn: number;
    expiresAt: number;
}

export class OAuth2Manager {
    private config: OAuth2Config;
    private tokens: Tokens | null = null;
    
    constructor(config: OAuth2Config) {
        this.config = config;
        this.loadTokensFromStorage();
    }
    
    // 1. Redirect user to provider login
    redirectToLogin(): void {
        const params = new URLSearchParams({
            client_id: this.config.clientId,
            redirect_uri: this.config.redirectUri,
            response_type: this.config.responseType,
            scope: this.config.scope,
            state: this.generateState(),
        });
        window.location.href = `${this.config.authorizationEndpoint}?${params}`;
    }
    
    // 2. Handle redirect callback after login
    async handleCallback(code: string): Promise<Tokens> {
        const tokens = await this.exchangeCodeForTokens(code);
        this.setTokens(tokens);
        return tokens;
    }
    
    // 3. Exchange authorization code for tokens
    private async exchangeCodeForTokens(code: string): Promise<Tokens> {
        const response = await fetch(this.config.tokenEndpoint, {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: new URLSearchParams({
                grant_type: this.config.grantType,
                code,
                client_id: this.config.clientId,
                redirect_uri: this.config.redirectUri,
            }),
        });
        
        if (!response.ok) throw new Error("Token exchange failed");
        
        const data = await response.json();
        return {
            accessToken: data.access_token,
            refreshToken: data.refresh_token,
            idToken: data.id_token,
            expiresIn: data.expires_in,
            expiresAt: Date.now() + (data.expires_in * 1000),
        };
    }
    
    // 4. Refresh access token when expired
    async refreshAccessToken(): Promise<string> {
        if (!this.tokens?.refreshToken) throw new Error("No refresh token");
        
        const response = await fetch(this.config.tokenEndpoint, {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: new URLSearchParams({
                grant_type: "refresh_token",
                refresh_token: this.tokens.refreshToken,
                client_id: this.config.clientId,
            }),
        });
        
        if (!response.ok) throw new Error("Token refresh failed");
        
        const data = await response.json();
        const newTokens = {
            ...this.tokens,
            accessToken: data.access_token,
            expiresIn: data.expires_in,
            expiresAt: Date.now() + (data.expires_in * 1000),
        };
        this.setTokens(newTokens);
        return newTokens.accessToken;
    }
    
    // 5. Logout and redirect to provider
    logout(): void {
        this.clearTokens();
        const params = new URLSearchParams({
            client_id: this.config.clientId,
            post_logout_redirect_uri: this.config.redirectUri,
        });
        window.location.href = `${this.config.logoutEndpoint}?${params}`;
    }
    
    // Token management
    getAccessToken(): string | null {
        if (this.isTokenExpired()) {
            return null; // Caller should refresh
        }
        return this.tokens?.accessToken || null;
    }
    
    isTokenExpired(): boolean {
        if (!this.tokens?.expiresAt) return true;
        return Date.now() > this.tokens.expiresAt - 60000; // 1 min buffer
    }
    
    setTokens(tokens: Tokens): void {
        this.tokens = tokens;
        this.saveTokensToStorage();
    }
    
    // Storage (secure in production)
    private saveTokensToStorage(): void {
        // In production: use httpOnly cookies for sensitive tokens
        // For now: sessionStorage (cleared on browser close)
        if (this.tokens) {
            sessionStorage.setItem("oauth2_tokens", JSON.stringify(this.tokens));
        }
    }
    
    private loadTokensFromStorage(): void {
        const stored = sessionStorage.getItem("oauth2_tokens");
        if (stored) {
            this.tokens = JSON.parse(stored);
        }
    }
    
    private clearTokens(): void {
        this.tokens = null;
        sessionStorage.removeItem("oauth2_tokens");
    }
    
    private generateState(): string {
        return Math.random().toString(36).substring(7);
    }
}

export default OAuth2Manager;
```

### Phase 2: Create OAuth2 Composable

**File**: `ui/src/composables/useOAuth2.ts` (NEW)

```typescript
import {ref, computed} from "vue";
import OAuth2Manager from "@/utils/oauth2";
import {useRouter} from "vue-router";

interface OAuth2ComposableOptions {
    config: any; // OAuth2Config from backend
}

export function useOAuth2(options: OAuth2ComposableOptions) {
    const router = useRouter();
    const manager = ref<OAuth2Manager | null>(null);
    const isLoading = ref(false);
    const error = ref<string | null>(null);
    
    // Initialize with config from backend
    const initialize = (config: any) => {
        manager.value = new OAuth2Manager({
            clientId: config.oauth2ClientId,
            redirectUri: `${window.location.origin}/ui/oauth2-callback`,
            authorizationEndpoint: config.oauth2AuthEndpoint,
            tokenEndpoint: config.oauth2TokenEndpoint,
            userInfoEndpoint: config.oauth2UserInfoEndpoint,
            logoutEndpoint: config.oauth2LogoutEndpoint,
            scope: config.oauth2Scope || "openid profile email",
        });
    };
    
    // Login: redirect to provider
    const login = () => {
        if (manager.value) {
            manager.value.redirectToLogin();
        }
    };
    
    // Handle callback after provider redirects back
    const handleCallback = async (code: string) => {
        isLoading.value = true;
        error.value = null;
        
        try {
            if (!manager.value) throw new Error("OAuth2 not initialized");
            
            await manager.value.handleCallback(code);
            await router.push({name: "home"});
        } catch (err: any) {
            error.value = err.message;
            await router.push({name: "login"});
        } finally {
            isLoading.value = false;
        }
    };
    
    // Logout
    const logout = () => {
        if (manager.value) {
            manager.value.logout();
        }
    };
    
    // Get current access token (refresh if needed)
    const getAccessToken = async (): Promise<string> => {
        if (!manager.value) throw new Error("OAuth2 not initialized");
        
        let token = manager.value.getAccessToken();
        if (!token) {
            token = await manager.value.refreshAccessToken();
        }
        return token;
    };
    
    const isAuthenticated = computed(() => {
        return manager.value?.getAccessToken() !== null;
    });
    
    return {
        manager,
        initialize,
        login,
        handleCallback,
        logout,
        getAccessToken,
        isAuthenticated,
        isLoading,
        error,
    };
}
```

### Phase 3: Update Axios Interceptor

**File**: `ui/src/utils/axios.ts` (MODIFY)

Key changes:

```typescript
// In the request interceptor:
const requestInterceptor = async (config: any) => {
    initProgress();
    
    // OLD: Authorization header set by BasicAuth
    // NEW: Get OAuth2 access token
    const accessToken = await getOAuth2AccessToken(); // New function
    if (accessToken) {
        config.headers.Authorization = `Bearer ${accessToken}`;
    }
    
    return config;
};

// In the error interceptor, handle 401:
const errorResponseInterceptor = (error: AxiosError) => {
    increaseProgress();
    
    if (error.response?.status === 401) {
        // Token expired or invalid
        // Trigger token refresh or redirect to login
        refreshOAuth2Token(); // New function
    }
    
    return Promise.reject(error);
};
```

### Phase 4: Create OAuth2 Login Component

**File**: `ui/src/components/basicauth/OAuth2Login.vue` (NEW or REPLACE)

```vue
<template>
    <div class="oauth2-login-container">
        <h1>Login with Keycloak</h1>
        <button @click="handleLogin" :disabled="isLoading">
            {{ isLoading ? "Redirecting..." : "Login" }}
        </button>
        <div v-if="error" class="error">{{ error }}</div>
    </div>
</template>

<script setup lang="ts">
import {useOAuth2} from "@/composables/useOAuth2";
import {useMiscStore} from "override/stores/misc";
import {onMounted} from "vue";

const miscStore = useMiscStore();
const {login, initialize, isLoading, error} = useOAuth2({config: null});

onMounted(async () => {
    const config = await miscStore.loadConfigs();
    initialize(config);
});

const handleLogin = () => {
    login();
};
</script>
```

### Phase 5: Create OAuth2 Callback Route

**File**: `ui/src/components/basicauth/OAuth2Callback.vue` (NEW)

```vue
<template>
    <div class="oauth2-callback-container">
        <div v-if="isLoading">
            <h2>Processing login...</h2>
            <p>Please wait while we complete your authentication.</p>
        </div>
        <div v-else-if="error" class="error">
            <h2>Login Error</h2>
            <p>{{ error }}</p>
            <router-link to="/ui/login">Back to login</router-link>
        </div>
    </div>
</template>

<script setup lang="ts">
import {useRoute} from "vue-router";
import {useOAuth2} from "@/composables/useOAuth2";
import {onMounted} from "vue";

const route = useRoute();
const {handleCallback, isLoading, error} = useOAuth2({config: null});

onMounted(async () => {
    const code = route.query.code as string;
    if (code) {
        await handleCallback(code);
    }
});
</script>
```

### Phase 6: Update Routes

**File**: `ui/src/routes/routes.js` (MODIFY)

```javascript
// Add OAuth2 callback route
{
    name: "oauth2-callback",
    path: "/ui/oauth2-callback",
    component: () => import("../components/basicauth/OAuth2Callback.vue"),
    meta: { anonymous: true }
},

// Update or replace login route
{
    name: "login",
    path: "/ui/login",
    component: () => import("../components/basicauth/OAuth2Login.vue"),
    meta: { anonymous: true }
},
```

### Phase 7: Create OAuth2 Store

**File**: `ui/src/stores/oauth2.ts` (NEW)

```typescript
import {defineStore} from "pinia";
import OAuth2Manager from "@/utils/oauth2";

interface State {
    manager: OAuth2Manager | null;
    isAuthenticated: boolean;
    accessToken: string | null;
    user: any;
    loading: boolean;
}

export const useOAuth2Store = defineStore("oauth2", {
    state: (): State => ({
        manager: null,
        isAuthenticated: false,
        accessToken: null,
        user: null,
        loading: false,
    }),

    actions: {
        initialize(config: any) {
            this.manager = new OAuth2Manager({
                clientId: config.oauth2ClientId,
                // ... other config
            });
            this.isAuthenticated = !!this.manager.getAccessToken();
        },

        async login() {
            if (this.manager) {
                this.manager.redirectToLogin();
            }
        },

        logout() {
            if (this.manager) {
                this.manager.logout();
                this.isAuthenticated = false;
                this.accessToken = null;
                this.user = null;
            }
        },

        async getAccessToken(): Promise<string> {
            if (!this.manager) throw new Error("OAuth2 not initialized");
            return this.manager.getAccessToken() || await this.manager.refreshAccessToken();
        },
    },
});
```

### Phase 8: Update Main Initialization

**File**: `ui/src/main.js` (MODIFY)

```javascript
// Replace BasicAuth checks with OAuth2
import {useOAuth2Store} from "./stores/oauth2";

// In router.beforeEach:
router.beforeEach(async (to, from, next) => {
    // Replace BasicAuth.isLoggedIn() with:
    const oauth2Store = useOAuth2Store();
    const configs = await miscStore.loadConfigs();
    
    oauth2Store.initialize(configs);
    
    if (to.meta?.anonymous === true) {
        return next();
    }
    
    if (!oauth2Store.isAuthenticated) {
        return next({name: "login"});
    }
    
    return next();
});

// Replace configureAxios BasicAuth logic with OAuth2 token injection
```

### Phase 9: Backend Configuration

The backend needs to provide OAuth2 configuration in the `/api/configs` endpoint:

```json
{
    "oauth2ClientId": "kestra-app",
    "oauth2AuthEndpoint": "https://keycloak.example.com/auth/realms/master/protocol/openid-connect/auth",
    "oauth2TokenEndpoint": "https://keycloak.example.com/auth/realms/master/protocol/openid-connect/token",
    "oauth2UserInfoEndpoint": "https://keycloak.example.com/auth/realms/master/protocol/openid-connect/userinfo",
    "oauth2LogoutEndpoint": "https://keycloak.example.com/auth/realms/master/protocol/openid-connect/logout",
    "oauth2Scope": "openid profile email",
    "isBasicAuthInitialized": false,
    "isOAuth2Enabled": true
}
```

---

## Migration Path

### Option A: Parallel (Recommended for Safety)

1. Keep BasicAuth working
2. Add OAuth2 alongside it
3. Use backend config to toggle between them
4. Gradually migrate users

```typescript
// In router guard:
if (config.isOAuth2Enabled) {
    // Use OAuth2 flow
} else {
    // Use BasicAuth flow
}
```

### Option B: Full Migration

1. Remove all BasicAuth code
2. Require OAuth2 everywhere
3. Faster but riskier

---

## File Changes Summary

### New Files to Create
```
ui/src/
├── utils/oauth2.ts                              # OAuth2 manager class
├── composables/useOAuth2.ts                     # OAuth2 composable
├── stores/oauth2.ts                             # OAuth2 Pinia store
└── components/basicauth/OAuth2Login.vue         # Login page (OAuth2)
└── components/basicauth/OAuth2Callback.vue      # Callback handler
```

### Files to Modify
```
ui/src/
├── utils/axios.ts                               # Update interceptors
├── main.js                                       # Update router guards
├── App.vue                                       # Update initialization
├── routes/routes.js                             # Add OAuth2 routes
└── utils/basicAuth.ts                           # Keep for backward compat OR remove
```

### Backend Files to Update
```
cli/src/main/resources/
└── application-override.yml                     # Add OAuth2 config

webserver/src/main/java/io/kestra/
└── controllers/ConfigController.java            # Expose OAuth2 config
```

---

## Keycloak Configuration Example

### 1. Create Keycloak Realm
```bash
# Access Keycloak admin console
# Navigate to: http://keycloak:8080/auth/admin

# Create realm: "master"
# Create client: "kestra-app"
# Set Valid Redirect URIs:
# - http://localhost:5173/ui/oauth2-callback
# - http://your-domain:5173/ui/oauth2-callback
```

### 2. Keycloak Client Settings
```
Client ID: kestra-app
Client Protocol: openid-connect
Access Type: public (or confidential with client secret)
Valid Redirect URIs: http://localhost:5173/ui/oauth2-callback
Web Origins: http://localhost:5173
```

### 3. Backend Config
```yaml
# application-override.yml
oauth2:
  enabled: true
  provider: keycloak
  keycloak:
    server-url: http://keycloak:8080/auth
    realm: master
    client-id: kestra-app
    client-secret: ${KEYCLOAK_CLIENT_SECRET:}
```

---

## Security Considerations

### Token Storage
- ❌ **Don't**: Store tokens in localStorage (XSS vulnerable)
- ✅ **Do**: Use httpOnly cookies (set by backend)
- ⚠️ **Acceptable**: sessionStorage (cleared on browser close)

### Token Refresh
- Refresh tokens should never be exposed to JavaScript
- Backend should handle refresh via secure HTTP-only cookies
- Use PKCE flow for additional security

### CORS
```yaml
# Backend CORS configuration
micronaut:
  server:
    cors:
      enabled: true
      configurations:
        all:
          allowedOrigins:
            - http://localhost:5173
          allowedMethods:
            - GET
            - POST
          allowedHeaders:
            - Authorization
            - Content-Type
          exposedHeaders:
            - Authorization
```

### State Parameter
- Always validate `state` parameter in callback to prevent CSRF
- Currently basic implementation - enhance for production

---

## Testing OAuth2 Flow

### 1. Test Login Redirect
```typescript
// In Firefox DevTools console
const {login} = useOAuth2({config: {...}});
login(); // Should redirect to Keycloak
```

### 2. Test Token Refresh
```typescript
// Check expired tokens are refreshed
const store = useOAuth2Store();
const token1 = await store.getAccessToken();
// Wait for expiration
const token2 = await store.getAccessToken(); // Should be refreshed
console.assert(token1 !== token2);
```

### 3. Test Logout
```typescript
const store = useOAuth2Store();
store.logout(); // Should redirect to Keycloak logout
```

---

## Common Issues & Solutions

### Issue: Redirect Loop
**Cause**: OAuth2 not properly initialized
**Solution**: Ensure config loaded before initializing OAuth2

### Issue: CORS Error on Token Endpoint
**Cause**: Backend doesn't allow token requests
**Solution**: Configure CORS on token endpoint or call from backend (better)

### Issue: Token Refresh Fails
**Cause**: Refresh token expired or invalid
**Solution**: Redirect to login and re-authenticate

### Issue: State Mismatch
**Cause**: State parameter validation failing
**Solution**: Check localStorage state matches response state

---

## Migration Checklist

- [ ] Create `utils/oauth2.ts`
- [ ] Create `composables/useOAuth2.ts`
- [ ] Create `stores/oauth2.ts`
- [ ] Create OAuth2 Login component
- [ ] Create OAuth2 Callback component
- [ ] Update `utils/axios.ts` for OAuth2 tokens
- [ ] Update `main.js` router guards
- [ ] Update `routes/routes.js` with callback route
- [ ] Test login flow locally
- [ ] Test token refresh
- [ ] Test logout
- [ ] Configure Keycloak realm and client
- [ ] Update backend config
- [ ] Test with actual Keycloak
- [ ] Update documentation
- [ ] Deploy to staging
- [ ] Test with real users

---

## Next Steps

1. **Start with Phase 1-2**: Create OAuth2 service layer and composable
2. **Test locally**: Use mock OAuth2 configuration
3. **Set up Keycloak**: Use Docker for local testing
4. **Integrate phases 3-8**: Update UI components
5. **Test full flow**: Login → token exchange → API calls → refresh → logout
6. **Migrate gradually**: Keep BasicAuth as fallback initially

This approach gives you:
- ✅ OAuth2/OIDC compliance
- ✅ Keycloak support (works with any OIDC provider)
- ✅ Secure token handling
- ✅ Flexible provider switching
- ✅ Backward compatibility option

# Vue Architecture Overview - Kestra SSO

This document explains the Vue.js architecture used in the Kestra SSO project.

## Project Stack

- **Vue 3** with Composition API
- **TypeScript** for type safety
- **Vite** for build tooling
- **Pinia** for state management
- **Vue Router** for routing
- **Axios** for HTTP requests
- **Element Plus** for UI components
- **ESLint & Prettier** for code quality

## Directory Structure

```
ui/src/
├── main.js                    # Application entry point
├── App.vue                    # Root component
├── assets/                    # Static assets (images, fonts, etc.)
├── components/                # Vue components (organized by feature)
├── composables/               # Vue Composition API composables (reusable logic)
├── models/                    # TypeScript interfaces and types
├── override/                  # Customizations and overrides
├── routes/                    # Vue Router configuration
├── services/                  # API service functions
├── stores/                    # Pinia state management stores
├── styles/                    # Global styles (SCSS)
├── translations/              # i18n translations
└── utils/                     # Utility functions and helpers
```

## Core Architecture Patterns

### 1. Application Initialization Flow

```
main.js
  ↓
initApp() - Sets up router, Pinia store, translations
  ↓
setupTenantRouter() - Configures multi-tenant routing
  ↓
configureAxios() - Sets up HTTP client with interceptors
  ↓
router.beforeEach() - Route guards for authentication
  ↓
app.mount("#app") - Mount Vue app to DOM
```

**File**: [main.js](src/main.js)

The initialization handles:
- Creating the Vue app instance
- Loading translation files (en.json)
- Initializing the router with auth guards
- Setting up the Pinia store
- Configuring axios with JWT token handling
- Checking authentication status before navigation

### 2. Routing Architecture

**File**: [routes/routes.js](src/routes/routes.js)

Routes are organized by feature:

```javascript
// Dashboard routes
{name: "home", path: "/:tenant?/dashboards/:dashboard?", ...}
{name: "dashboards/create", path: "/:tenant?/dashboards/new", ...}

// Flow routes
{name: "flows/list", path: "/:tenant?/flows", ...}
{name: "flows/create", path: "/:tenant?/flows/new", ...}
{name: "flows/update", path: "/:tenant?/flows/edit/:namespace/:id/:tab?", ...}

// Execution routes
{name: "executions/list", path: "/:tenant?/executions", ...}

// KV, Secrets, etc.
{name: "kv/list", path: "/:tenant?/kv", ...}
{name: "secrets/list", path: "/:tenant?/secrets", ...}
```

**Key Features**:
- **Lazy loading**: Components loaded with `() => import("...")`
- **Dynamic layouts**: Routes can specify custom layouts via `meta.layout`
- **Route guards**: `beforeEnter` hooks for validation
- **Tenant support**: `:tenant?` parameter for multi-tenancy
- **Anonymous routes**: `meta.anonymous = true` for login/setup pages

### 3. State Management (Pinia)

**Location**: [stores/](src/stores/)

Stores are feature-based and use Pinia's composition API:

```typescript
// Example store structure
export const useFlowStore = defineStore("flow", {
    state: () => ({
        flows: [],
        selectedFlow: null,
        loading: false,
    }),
    
    getters: {
        getFlowById: (state) => (id) => state.flows.find(f => f.id === id),
    },
    
    actions: {
        async fetchFlows() {
            // Make API calls using this.$http
        },
        selectFlow(flow) {
            this.selectedFlow = flow;
        },
    },
});
```

**Main Stores**:
- **core**: Global app state (messages, errors)
- **layout**: UI layout state
- **api**: External API data (feeds, config)
- **flow**: Flow definitions and metadata
- **executions**: Execution history and status
- **dashboard**: Dashboard configurations
- **plugins**: Plugin management
- **doc**: Documentation URLs and resources
- **misc**: Miscellaneous configurations (from override)
- **auth**: Authentication state (from override)

**Usage in Components**:
```typescript
import {useFlowStore} from "@/stores/flow"

const flowStore = useFlowStore()
// Access state
const flows = flowStore.flows
// Call actions
await flowStore.fetchFlows()
// Use getters
const myFlow = flowStore.getFlowById(123)
```

### 4. HTTP Client Configuration

**File**: [utils/axios.ts](src/utils/axios.ts)

Features:
- **Request interceptor**: Tracks loading progress with NProgress
- **Response interceptor**: Handles success responses
- **Error interceptor**: Handles 401, 403, and other errors
- **JWT token refresh**: Auto-refreshes expired tokens
- **CORS configuration**: Handles cross-origin requests
- **Request queuing**: Queues requests during token refresh

**Axios Setup**:
```typescript
configureAxios((instance) => {
    // Inject axios instance into Vue app
    app.use(VueAxios, instance);
    app.provide("axios", instance);
    
    // Make available to Pinia stores
    piniaStore.use(({store}) => {
        store.$http = instance;
    });
})
```

### 5. Component Organization

**Location**: [components/](src/components/)

Components are organized by feature domain:

```
components/
├── admin/          # Admin panel components
├── basicauth/      # Authentication components
├── dashboard/      # Dashboard views and editors
├── executions/     # Execution list/details
├── flows/          # Flow editor and list
├── kv/            # Key-value store UI
├── secrets/        # Secrets management
├── settings/       # Application settings
├── layout/         # Layout components (wrappers, headers, sidebars)
├── filter/         # Filter UI components
├── utils/          # Reusable utility components
└── ...
```

**Component Types**:

1. **Layout Components** (`layout/`):
   - `DefaultLayout.vue` - Main app layout with sidebar
   - `FullScreenLayout.vue` - Full screen, no borders
   - `OnlyLeftMenuLayout.vue` - Sidebar only

2. **Feature Components**:
   - `<FeatureList.vue>` - Lists items in a feature
   - `<FeatureCreate.vue>` - Create new item
   - `<FeatureEdit.vue>` - Edit existing item
   - `<FeatureRoot.vue>` - Container/router view

3. **Reusable Components**:
   - `<ErrorToast.vue>` - Error notifications
   - `<Tabs.vue>` - Tab navigation
   - `<Drawer.vue>` - Side drawer
   - `<MultiPanelEditorTabs.vue>` - Multi-panel editor

### 6. Composables (Reusable Logic)

**Location**: [composables/](src/composables/)

Composables encapsulate reusable stateful logic:

```typescript
// useFilters.ts - Filter management
export function useFilters(prefix: string) {
    const getSavedItems = () => { /* ... */ }
    const setSavedItems = (value) => { /* ... */ }
    return { getSavedItems, setSavedItems, ... }
}

// Usage in component
const { getSavedItems } = useFilters("flows")
const saved = getSavedItems()

// useTenant.ts - Multi-tenant support
export function setupTenantRouter(router, app) { /* ... */ }

// usePosthog.ts - Analytics integration
export async function initPostHogForSetup(config) { /* ... */ }
```

**Key Composables**:
- `useFilters` - Save/load filter preferences
- `useTenant` - Tenant routing and switching
- `usePosthog` - Analytics tracking
- `useUnsavedChangesDialog` - Warn before leaving unsaved changes
- `useRestoreUrl` - Restore previous route
- `useTableColumns` - Manage table column visibility
- `useScrollMemory` - Remember scroll position

### 7. Data Flow & API Calls

**Typical Flow**:

```
Component
  ↓
User Action (click, form submit)
  ↓
Call Store Action
  ↓
Store Action calls this.$http (axios)
  ↓
Axios request with interceptors
  ↓
Backend API (/api/...)
  ↓
Response → Store updates state
  ↓
Component reactivity re-renders
```

**Example**:
```typescript
// Component
const flowStore = useFlowStore()

async function loadFlows() {
    await flowStore.fetchFlows() // Call store action
}

// Store (stores/flow.ts)
actions: {
    async fetchFlows() {
        try {
            this.loading = true
            const response = await this.$http.get('/api/flows')
            this.flows = response.data  // Update state
            this.loading = false
        } catch (error) {
            // Error handling
        }
    }
}
```

### 8. Authentication & Authorization

**File**: [utils/basicAuth.ts](src/utils/basicAuth.ts) & [stores/auth.ts (override)](override/stores/auth.ts)

**Flow**:
1. User enters credentials on login page
2. Credentials encoded in Basic Auth header
3. Backend validates and returns JWT token
4. JWT token stored in localStorage
5. Axios interceptor adds token to every request
6. On 401 response → token refresh or redirect to login

**Route Protection** (main.js):
```typescript
router.beforeEach(async (to, from, next) => {
    // Check if basic auth initialized
    // Check for valid credentials
    // Redirect to login if needed
    // Check for unsaved changes
    // Redirect to welcome page if first time
})
```

### 9. State Persistence

**Mechanisms**:
1. **localStorage** - Filter preferences, auth tokens, UI state
2. **Pinia persist plugin** - Auto-save store state (if configured)
3. **sessionStorage** - Temporary session data
4. **URL params** - Route state (query filters, pagination)

**Example**:
```typescript
// Save to localStorage
localStorage.setItem("basicAuthSetupInProgress", "true")

// Check auth state
const isSetupInProgress = localStorage.getItem("basicAuthSetupInProgress") === "true"

// Filter preferences
useFilters("flows").setSavedItems([{name: "Active", value: "status:active"}])
```

### 10. Multi-Tenancy Support

**File**: [composables/useTenant.ts](src/composables/useTenant.ts)

Routes include optional tenant parameter:
```typescript
path: "/:tenant?/dashboards/:dashboard?"
```

**Usage**:
- URLs: `/tenant-name/flows`, `/tenant-name/executions`, etc.
- Tenant info available via route params: `route.params.tenant`
- Store tracks current tenant context
- API calls include tenant in URL or headers

## Lifecycle: From Click to Display

### 1. User clicks "Create Flow" button
```
Component: FlowsList.vue
├─ @click="createFlow()"
└─ Router.push({name: "flows/create"})
```

### 2. Router navigates to new route
```
Route Guard: router.beforeEach()
├─ Check authentication
├─ Check unsaved changes
└─ Navigate to FlowCreate.vue
```

### 3. Component mounts and loads data
```
FlowCreate.vue
├─ onMounted()
├─ const flowStore = useFlowStore()
├─ await flowStore.loadTemplates()
└─ UI renders with data
```

### 4. User submits form
```
FlowCreate.vue
├─ handleSubmit()
├─ flowStore.createFlow(data)
│  ├─ Call API: POST /api/flows
│  ├─ Axios interceptor adds auth
│  ├─ Backend validates & creates
│  └─ Update state: flows.push(newFlow)
└─ Router.push({name: "flows/update", ...})
```

### 5. Data displayed in UI
```
Pinia reactivity
├─ State changed: flows.push(newFlow)
├─ Getters recalculated
└─ Component re-renders automatically
```

## Performance Optimization

1. **Lazy Loading**: Components loaded on-demand with `() => import(...)`
2. **Code Splitting**: Each route gets its own chunk
3. **Caching**: Pinia stores cache API responses
4. **Progress Bar**: NProgress shows loading state
5. **Request Queueing**: Requests queued during token refresh
6. **Memoization**: Getters cache computed values

## Best Practices Used

1. ✅ **Composition API** - Modern Vue 3 approach
2. ✅ **TypeScript** - Type safety in stores and composables
3. ✅ **Component-based** - Reusable, testable components
4. ✅ **Separation of concerns** - Stores, composables, components
5. ✅ **Lazy loading** - Only load components when needed
6. ✅ **Error handling** - Try-catch in async actions
7. ✅ **Loading states** - User feedback during API calls
8. ✅ **Route guards** - Protect routes that need auth
9. ✅ **Constants** - Centralized config values
10. ✅ **i18n ready** - Support for multiple languages

## Adding a New Feature

### Step 1: Create store (stores/myfeature.ts)
```typescript
export const useMyFeatureStore = defineStore("myfeature", {
    state: () => ({items: []}),
    actions: {
        async loadItems() {
            const response = await this.$http.get("/api/myfeature")
            this.items = response.data
        }
    }
})
```

### Step 2: Create routes (routes/routes.js)
```javascript
{name: "myfeature/list", path: "/:tenant?/myfeature", component: () => import("../components/myfeature/List.vue")}
{name: "myfeature/create", path: "/:tenant?/myfeature/new", component: () => import("../components/myfeature/Create.vue")}
```

### Step 3: Create components
```
components/myfeature/
├── List.vue          # Show all items
├── Create.vue        # Create new item
├── Edit.vue          # Edit existing item
└── MyFeatureRoot.vue # Router container
```

### Step 4: Use in component
```typescript
const myfeatureStore = useMyFeatureStore()
onMounted(() => myfeatureStore.loadItems())
```

## Debugging Tips

1. **Vue DevTools** - Inspect components and state
2. **Pinia DevTools** - Time-travel debugging of store actions
3. **Network tab** - See API requests/responses
4. **Console** - Check for errors and logs
5. **Breakpoints** - Debug with VS Code Firefox debugger
6. **localStorage** - Check persisted state: `localStorage.mykey`

## Common Patterns

### Making API Call in Component
```typescript
const store = useMyStore()
onMounted(async () => {
    try {
        await store.loadData()
    } catch (error) {
        console.error("Failed to load:", error)
    }
})
```

### Watching Store State
```typescript
watch(() => store.selectedItem, (newVal) => {
    // React to changes
})
```

### Conditional Rendering
```vue
<div v-if="store.loading">Loading...</div>
<div v-else-if="store.items.length > 0">
    <Item v-for="item in store.items" :key="item.id" :item="item" />
</div>
<div v-else>No items found</div>
```

### Form Handling
```vue
<form @submit.prevent="handleSubmit">
    <input v-model="form.name" required />
    <button type="submit" :disabled="store.loading">Submit</button>
</form>
```

---

This architecture provides a scalable, maintainable structure for building complex Vue.js applications with clear separation of concerns and reusable patterns.

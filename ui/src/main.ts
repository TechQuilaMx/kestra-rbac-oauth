import {createApp} from "vue"
import type {Router} from "vue-router"

import "./utils/monacoEnvironment"
import {setupPreloadErrorReloadHandler} from "./utils/preloadErrorReload"

setupPreloadErrorReloadHandler()

import App from "./App.vue"
import initApp from "./utils/init"
import {setupKestraHttp} from "./utils/kestraHttp"
import {useClient} from "@kestra-io/kestra-sdk"
import routes from "./routes/routes"
import en from "./translations/en.json"
import {setupTenantRouter} from "./composables/useTenant"
import * as BasicAuth from "./utils/basicAuth"
import {getCsrfToken} from "./utils/csrf"
import {useCoreStore} from "./stores/core"
import {useLayoutStore} from "./stores/layout"
import {useUnsavedChangesStore} from "./stores/unsavedChanges"
import {useMiscStore} from "override/stores/misc"
import {useOAuth2Store} from "./stores/oauth2"
import {vPermission, vRole, vAdmin} from "./directives/permissions"
import {TASK_ICON_INJECTION_KEY} from "@kestra-io/design-system"
import TaskIcon from "./components/plugins/TaskIcon.vue"
import {registerServiceWorker} from "./utils/serviceWorker"
import {initPwaInstallCapture} from "./utils/pwaInstallState"

void registerServiceWorker()
initPwaInstallCapture()

const app = createApp(App)

// Register RBAC permission directives
app.directive("permission", vPermission)
app.directive("role", vRole)
app.directive("admin", vAdmin)

// lets KsEditor and the topology package render real plugin icons without
// the design system depending on the app's plugin-icon API
app.provide(TASK_ICON_INJECTION_KEY, TaskIcon)

// Fail closed: an error probing the pre-auth endpoints is no evidence that setup is needed.
const handleAuthError = (to: {fullPath: string}, error: unknown) => {
    if ((error as {response?: {status?: number}} | null)?.response?.status === 401) {
        BasicAuth.logout()
        const fromPath = to.fullPath !== "/ui/login" ? to.fullPath : undefined
        return {name: "login", query: fromPath ? {from: fromPath} : {}}
    } 
    console.error("Error during authentication check:", error)
    return
}

let httpClient: ReturnType<typeof setupKestraHttp> | undefined

function setupAxios(router: Router) {
    const coreStore = useCoreStore()
    const unsavedChangesStore = useUnsavedChangesStore()
    const layoutStore = useLayoutStore()

    function beforeLogout() {
        document.body.classList.add("login")
        unsavedChangesStore.unsavedChange = false
        layoutStore.setTopNavbar(undefined)
        BasicAuth.logout()
    }

    httpClient = setupKestraHttp({}, {
        coreStore,
        router,
        beforeLogout,
        isLoggedIn: () => !!BasicAuth.isLoggedIn(),
    })

    // Add Bearer token and CSRF token to every outgoing request
    httpClient.interceptors.request.use(async (request) => {
        const headers = new Headers(request.headers)

        // Inject OAuth2 Bearer token if present
        try {
            const oauth2Store = useOAuth2Store()
            if (oauth2Store.hasTokens) {
                const token = await oauth2Store.getAccessToken()
                if (token) {
                    headers.set("Authorization", `Bearer ${token}`)
                }
            }
        } catch (e) {
            console.warn("Could not inject OAuth2 token into request", e)
        }

        const csrfToken = getCsrfToken()
        if (csrfToken) {
            headers.set("X-CSRF-TOKEN", csrfToken)
        }
        return new Request(request, {headers})
    })

    return useClient()
}

// FIXME: any - guard args are untyped in the GuardFn interface
async function beforeResolve(router: Router, to: any, from: any): Promise<unknown> { // FIXME: any
    if(to.path === from.path && to.query === from.query) {
        return // Prevent navigation if the path and query are the same
    }

    try {
        const miscStore = useMiscStore()
        if(!httpClient) {
            setupAxios(router)
        }
        const loginConfig = await miscStore.loadLoginConfig()

        // Initialize OAuth2 from pre-auth login configuration if configured
        const oauth2Store = useOAuth2Store()
        if (loginConfig.oauth2ClientId && !oauth2Store.isInitialized) {
            oauth2Store.initialize(loginConfig)
        }

        // Handle OAuth2 authentication flow
        if (loginConfig.oauth2ClientId && oauth2Store.isInitialized) {
            if ((to as {meta?: {anonymous?: boolean}}).meta?.anonymous === true) {
                return
            }

            if (!oauth2Store.isAuthenticated && !oauth2Store.hasTokens) {
                const fromPath = to.fullPath !== "/ui/login" ? to.fullPath : undefined
                return {name: "login", query: fromPath ? {from: fromPath} : {}}
            }

            if (oauth2Store.hasTokens && !oauth2Store.isAuthenticated) {
                oauth2Store.isAuthenticated = true
                void oauth2Store.getAccessToken().then(() => oauth2Store.fetchUserInfo())
            }

            await miscStore.loadConfigs()
            return
        }

        // Fall back to Basic Auth handling
        if(!loginConfig.isBasicAuthInitialized) {
            // Since, Configs takes preference
            // we need to check if any regex validation error in BE.
            const validationErrors = await miscStore.loadBasicAuthValidationErrors()

            if (validationErrors?.length > 0) {
                // Creds exist in config but failed validation
                // Route to login to show errors
                if (to.name === "login") {
                    return
                }

                return {name: "login"}
            } else {
                // No creds in config - redirect to set it up
                if (to.name === "setup") {
                    return
                }

                return {name: "setup"}
            }
        }

        if ((to as {meta?: {anonymous?: boolean}}).meta?.anonymous === true) {
            if (to.name === "setup") {
                return {name: "login"}
            }
            return
        }

        const hasCredentials = BasicAuth.isLoggedIn()

        if (!hasCredentials) {
            const fromPath = to.fullPath !== "/ui/login" ? to.fullPath : undefined
            return {name: "login", query: fromPath ? {from: fromPath} : {}}
        }

        // Check if basic auth setup is still in progress
        const isSetupInProgress = localStorage.getItem("basicAuthSetupInProgress")
        if (isSetupInProgress === "true") {
            return {name: "setup"}
        }

        // Now that the user is authenticated, load the full instance configuration.
        await miscStore.loadConfigs()
    } catch (error) {
        console.error("Error during authentication check:", error)
        return handleAuthError(to, error)
    }
}

initApp(app, routes, null, en as Record<string, unknown>, {}, {beforeResolve: beforeResolve as (...args: unknown[]) => unknown}).then(({router, piniaStore}) => {

    // Setup tenant router
    setupTenantRouter(router, app)

    setupAxios(router)

    const $http = setupAxios(router)

    piniaStore.use(({store: piniaStoreLocal}) => {
        // FIXME: any
        ;(piniaStoreLocal as any).$http = $http
    })

    // mount
    router.isReady().then(() => app.mount("#app"))
})

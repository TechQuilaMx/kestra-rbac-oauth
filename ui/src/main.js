import {createApp} from "vue"
import VueAxios from "vue-axios";

import App from "./App.vue"
import initApp from "./utils/init"
import configureAxios from "./utils/axios"
import routes from "./routes/routes";
import en from "./translations/en.json";
import {setupTenantRouter} from "./composables/useTenant";
import * as BasicAuth from "./utils/basicAuth";
import {useMiscStore} from "override/stores/misc";
import {useOAuth2Store} from "./stores/oauth2";
import {vPermission, vRole, vAdmin} from "./directives/permissions";

import {shouldShowWelcome, isDashboardRoute} from "./utils/welcomeGuard";

const app = createApp(App)

// Register permission directives
app.directive('permission', vPermission);
app.directive('role', vRole);
app.directive('admin', vAdmin);

const handleAuthError = (error, to) => {
    if (error.message?.includes("401")) {
        BasicAuth.logout()
        const fromPath = to.fullPath !== "/ui/login" ? to.fullPath : undefined
        return {name: "login", query: fromPath ? {from: fromPath} : {}}
    }
    return {name: "setup"}
}

initApp(app, routes, null, en).then(({router, piniaStore}) => {
    router.beforeEach(async (to, from, next) => {
        if(to.path === from.path && to.query === from.query) {
            return next(); // Prevent navigation if the path and query are the same
        }

        try {
            const miscStore = useMiscStore();
            const configs = await miscStore.loadConfigs();
            
            // Initialize OAuth2 if enabled
            const oauth2Store = useOAuth2Store();
            if (configs.oauth2ClientId) {
                oauth2Store.initialize(configs);
                console.log("OAuth2 initialized. isAuthenticated:", oauth2Store.isAuthenticated, "hasTokens:", oauth2Store.hasTokens);
            }

            // Handle OAuth2 authentication
            if (configs.oauth2ClientId && oauth2Store.isInitialized) {
                // Allow anonymous routes (login, callback, setup)
                if (to.meta?.anonymous === true) {
                    return next();
                }

                // Check if user is authenticated via OAuth2
                if (!oauth2Store.isAuthenticated && !oauth2Store.hasTokens) {
                    console.log("OAuth2 not authenticated, redirecting to login");
                    const fromPath = to.fullPath !== "/ui/login" ? to.fullPath : undefined
                    return next({name: "login", query: fromPath ? {from: fromPath} : {}})
                }

                // Update isAuthenticated if tokens exist
                if (oauth2Store.hasTokens && !oauth2Store.isAuthenticated) {
                    oauth2Store.isAuthenticated = true;
                    // Ensure access token is refreshed if needed and user info is loaded
                    oauth2Store.getAccessToken().then(() => oauth2Store.fetchUserInfo());
                }

                // Check welcome flow for dashboard routes
                if (isDashboardRoute(to.name) && await shouldShowWelcome()) {
                    return next({
                        name: "welcome",
                        params: {tenant: to.params.tenant}
                    });
                }

                return next();
            }

            // Fallback to BasicAuth if OAuth2 not configured
            if(!configs.isBasicAuthInitialized) {
                // Since, Configs takes preference
                // we need to check if any regex validation error in BE.
                const validationErrors = await miscStore.loadBasicAuthValidationErrors()

                if (validationErrors?.length > 0) {
                    // Creds exist in config but failed validation
                    // Route to login to show errors
                    if (to.name === "login") {
                        return next();
                    }

                    return next({name: "login"})
                } else {
                    // No creds in config - redirect to set it up
                    if (to.name === "setup") {
                        return next();
                    }

                    return next({name: "setup"})
                }
            }

            if (to.meta?.anonymous === true) {
                if (to.name === "setup") {
                    return next({name: "login"});
                }
                return next();
            }

            const hasCredentials = BasicAuth.isLoggedIn()

            if (!hasCredentials) {
                const fromPath = to.fullPath !== "/ui/login" ? to.fullPath : undefined
                return next({name: "login", query: fromPath ? {from: fromPath} : {}})
            }

            // Check if basic auth setup is still in progress
            const isSetupInProgress = localStorage.getItem("basicAuthSetupInProgress")
            if (isSetupInProgress === "true") {
                return next({name: "setup"})
            }

            if (isDashboardRoute(to.name) && await shouldShowWelcome()) {
                return next({
                    name: "welcome",
                    params: {tenant: to.params.tenant}
                });
            } 

            return next();
        } catch (error) {
            console.error("Error during authentication check:", error);
            return next(handleAuthError(error, to))
        }
    });

    // Setup tenant router
    setupTenantRouter(router, app);

    // axios
    configureAxios((instance) => {
        app.use(VueAxios, instance);
        app.provide("axios", instance);
        piniaStore.use(({store: piniaStoreLocal}) => {
            piniaStoreLocal.$http = instance;
        });
    }, null, router, true);

    // mount
    router.isReady().then(() => app.mount("#app"))
});


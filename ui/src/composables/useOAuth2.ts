import {ref, computed, Ref} from "vue";
import OAuth2Manager, {type OAuth2Config} from "@/utils/oauth2";
import {useRouter} from "vue-router";

interface OAuth2ComposableOptions {
    config?: OAuth2Config | null;
}

export function useOAuth2(options: OAuth2ComposableOptions = {}) {
    const router = useRouter();
    const manager: Ref<OAuth2Manager | null> = ref(null);
    const isLoading = ref(false);
    const error = ref<string | null>(null);

    /**
     * Initialize OAuth2Manager with configuration
     */
    const initialize = (config: any) => {
        try {
            if (!config) {
                throw new Error("OAuth2 config not provided");
            }

            const oauth2Config: OAuth2Config = {
                clientId: config.oauth2ClientId,
                redirectUri: `${window.location.origin}/ui/oauth2-callback`,
                authorizationEndpoint: config.oauth2AuthEndpoint,
                tokenEndpoint: config.oauth2TokenEndpoint,
                userInfoEndpoint: config.oauth2UserInfoEndpoint,
                logoutEndpoint: config.oauth2LogoutEndpoint,
                scope: config.oauth2Scope || "openid profile email",
                responseType: "code",
                grantType: "authorization_code",
                clientSecret: config.oauth2ClientSecret,
            };

            manager.value = new OAuth2Manager(oauth2Config);
            error.value = null;
        } catch (err: any) {
            error.value = err.message;
            console.error("Failed to initialize OAuth2:", err);
        }
    };

    /**
     * Redirect user to OAuth2 provider login
     */
    const login = () => {
        try {
            if (!manager.value) {
                throw new Error("OAuth2 not initialized");
            }
            manager.value.redirectToLogin();
        } catch (err: any) {
            error.value = err.message;
            console.error("Login error:", err);
        }
    };

    /**
     * Handle OAuth2 callback after provider redirects back
     */
    const handleCallback = async (code: string, state: string) => {
        isLoading.value = true;
        error.value = null;

        try {
            if (!manager.value) {
                throw new Error("OAuth2 not initialized");
            }

            await manager.value.handleCallback(code, state);
            await router.push({name: "home"});
        } catch (err: any) {
            error.value = err.message;
            console.error("Callback error:", err);
            // Redirect to login on error
            await router.push({name: "login"});
        } finally {
            isLoading.value = false;
        }
    };

    /**
     * Logout and redirect to provider
     */
    const logout = () => {
        try {
            if (!manager.value) {
                throw new Error("OAuth2 not initialized");
            }
            manager.value.logout();
        } catch (err: any) {
            error.value = err.message;
            console.error("Logout error:", err);
        }
    };

    /**
     * Get current access token (refresh if expired)
     */
    const getAccessToken = async (): Promise<string | null> => {
        try {
            if (!manager.value) {
                return null;
            }

            let token = manager.value.getAccessToken();

            if (!token && manager.value.hasTokens()) {
                // Token expired, try to refresh
                token = await manager.value.refreshAccessToken();
            }

            return token;
        } catch (err: any) {
            error.value = err.message;
            console.error("Failed to get access token:", err);
            return null;
        }
    };

    /**
     * Check if user is authenticated
     */
    const isAuthenticated = computed(() => {
        return manager.value?.hasTokens() ?? false;
    });

    /**
     * Check if token needs refresh
     */
    const isTokenExpired = computed(() => {
        return manager.value?.isTokenExpired() ?? true;
    });

    return {
        manager,
        initialize,
        login,
        handleCallback,
        logout,
        getAccessToken,
        isAuthenticated,
        isTokenExpired,
        isLoading,
        error,
    };
}

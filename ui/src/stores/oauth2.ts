import {defineStore} from "pinia";
import OAuth2Manager, {type OAuth2Config} from "../utils/oauth2";

interface State {
    manager: OAuth2Manager | null;
    isAuthenticated: boolean;
    accessToken: string | null;
    isInitialized: boolean;
    isLoading: boolean;
}

export const useOAuth2Store = defineStore("oauth2", {
    state: (): State => ({
        manager: null,
        isAuthenticated: false,
        accessToken: null,
        isInitialized: false,
        isLoading: false,
    }),

    getters: {
        isTokenExpired: (state) => {
            return state.manager?.isTokenExpired() ?? true;
        },

        hasTokens: (state) => {
            return state.manager?.hasTokens() ?? false;
        },

        getManager: (state) => {
            return state.manager;
        },
    },

    actions: {
        /**
         * Initialize OAuth2 with configuration from backend
         */
        initialize(config: any) {
            try {
                if (!config || !config.oauth2ClientId) {
                    console.warn("OAuth2 config not provided or incomplete");
                    return;
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

                this.manager = new OAuth2Manager(oauth2Config);
                this.isAuthenticated = this.manager.hasTokens();
                this.isInitialized = true;

                if (this.isAuthenticated) {
                    this.accessToken = this.manager.getAccessToken();
                }
            } catch (error) {
                console.error("Failed to initialize OAuth2 store:", error);
            }
        },

        /**
         * Redirect to OAuth2 provider login
         */
        login() {
            if (!this.manager) {
                throw new Error("OAuth2 not initialized");
            }
            this.manager.redirectToLogin();
        },

        /**
         * Handle callback after OAuth2 provider redirects back
         */
        async handleCallback(code: string, state: string) {
            this.isLoading = true;

            try {
                if (!this.manager) {
                    throw new Error("OAuth2 not initialized");
                }

                await this.manager.handleCallback(code, state);
                this.isAuthenticated = true;
                this.accessToken = this.manager.getAccessToken();
            } finally {
                this.isLoading = false;
            }
        },

        /**
         * Logout and redirect to provider logout endpoint
         */
        logout() {
            if (!this.manager) {
                throw new Error("OAuth2 not initialized");
            }

            this.isAuthenticated = false;
            this.accessToken = null;
            this.manager.logout();
        },

        /**
         * Get access token, refresh if needed
         */
        async getAccessToken(): Promise<string | null> {
            if (!this.manager) {
                console.error("OAuth2 not initialized");
                return null;
            }

            try {
                let token = this.manager.getAccessToken();

                if (!token && this.manager.hasTokens()) {
                    // Token expired, refresh it
                    token = await this.manager.refreshAccessToken();
                    this.accessToken = token;
                }

                return token;
            } catch (error) {
                console.error("Failed to get access token:", error);
                // Clear authentication on token refresh failure
                this.isAuthenticated = false;
                this.accessToken = null;
                return null;
            }
        },

        /**
         * Refresh access token manually
         */
        async refreshAccessToken(): Promise<string> {
            if (!this.manager) {
                throw new Error("OAuth2 not initialized");
            }

            try {
                const token = await this.manager.refreshAccessToken();
                this.accessToken = token;
                return token;
            } catch (error) {
                console.error("Token refresh failed:", error);
                this.isAuthenticated = false;
                this.accessToken = null;
                throw error;
            }
        },
    },
});

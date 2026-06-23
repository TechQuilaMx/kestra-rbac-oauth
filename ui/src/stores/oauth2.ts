import {defineStore} from "pinia";
import OAuth2Manager, {type OAuth2Config} from "../utils/oauth2";
import {useAxios} from "../utils/axios";

interface UserInfo {
    authenticated: boolean;
    username?: string;
    email?: string;
    name?: string;
    roles?: string[];
    permissions?: string[];
    isAdmin?: boolean;
}

interface State {
    manager: OAuth2Manager | null;
    isAuthenticated: boolean;
    accessToken: string | null;
    isInitialized: boolean;
    isLoading: boolean;
    userInfo: UserInfo | null;
}

export const useOAuth2Store = defineStore("oauth2", {
    state: (): State => ({
        manager: null,
        isAuthenticated: false,
        accessToken: null,
        isInitialized: false,
        isLoading: false,
        userInfo: null,
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
        
        /**
         * Check if user has a specific role
         */
        hasRole: (state) => (role: string) => {
            return state.userInfo?.roles?.includes(role.toLowerCase()) ?? false;
        },
        
        /**
         * Check if user has a specific permission
         */
        hasPermission: (state) => (permission: string) => {
            return state.userInfo?.permissions?.includes(permission) ?? false;
        },
        
        /**
         * Check if user is admin
         */
        isAdmin: (state) => {
            return state.userInfo?.isAdmin ?? false;
        },
    },

    actions: {
        /**
         * Initialize OAuth2 with configuration from backend
         */
        initialize(config: any) {
            try {
                // Skip if already initialized
                if (this.isInitialized && this.manager) {
                    // Re-check authentication status in case tokens were saved
                    this.isAuthenticated = this.manager.hasTokens();
                    if (this.isAuthenticated) {
                        this.getAccessToken().then(() => this.fetchUserInfo());
                    }
                    return;
                }

                if (!config || !config.oauth2ClientId) {
                    console.warn("OAuth2 config not provided or incomplete");
                    return;
                }

                console.log("OAuth2 Config from backend:", {
                    clientId: config.oauth2ClientId,
                    authEndpoint: config.oauth2AuthEndpoint,
                    tokenEndpoint: config.oauth2TokenEndpoint,
                    userInfoEndpoint: config.oauth2UserInfoEndpoint,
                    logoutEndpoint: config.oauth2LogoutEndpoint,
                    scope: config.oauth2Scope,
                });

                const oauth2Config: OAuth2Config = {
                    clientId: config.oauth2ClientId,
                    redirectUri: `${window.location.origin}/ui/oauth2-callback`,
                    postLogoutRedirectUri: `${window.location.origin}/ui/login`,
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
                    this.getAccessToken().then(() => this.fetchUserInfo());
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
                
                // Fetch user info after successful authentication
                await this.fetchUserInfo();
            } finally {
                this.isLoading = false;
            }
        },
        
        /**
         * Fetch current user info from backend
         */
        async fetchUserInfo() {
            try {
                if (!this.isAuthenticated || !this.accessToken) {
                    return;
                }
                const axios = useAxios();
                const response = await axios.get("/api/v1/user/me");
                this.userInfo = response.data;
                console.log("User info loaded:", this.userInfo);
            } catch (error) {
                console.error("Failed to fetch user info:", error);
                this.userInfo = null;
            }
        },

        /**
         * Logout and redirect to provider logout endpoint
         */
        async logout(): Promise<void> {
            try {
                if (!this.manager) {
                    // nothing to do, resolve
                    this.isAuthenticated = false;
                    this.accessToken = null;
                    this.userInfo = null;
                    return;
                }

                this.isAuthenticated = false;
                this.accessToken = null;
                this.userInfo = null;
                // manager.logout may redirect the browser; keep it synchronous
                this.manager.logout();
            } catch (e) {
                console.error("Error during logout:", e);
                // ensure a Promise rejection is possible for callers
                return Promise.reject(e);
            }
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

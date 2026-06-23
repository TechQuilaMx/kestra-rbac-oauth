/**
 * OAuth2 Manager
 * Handles OAuth2 authentication flow with support for any OIDC-compliant provider
 * Tested with Keycloak but works with Auth0, Google, GitHub, etc.
 */

export interface OAuth2Config {
    clientId: string;
    redirectUri: string;
    postLogoutRedirectUri?: string;
    authorizationEndpoint: string;  // e.g., https://keycloak.com/auth/...auth
    tokenEndpoint: string;           // e.g., https://keycloak.com/auth/.../token
    userInfoEndpoint: string;        // e.g., https://keycloak.com/auth/.../userinfo
    logoutEndpoint: string;          // e.g., https://keycloak.com/auth/.../logout
    scope: string;                   // e.g., "openid profile email"
    responseType?: string;           // default: "code"
    grantType?: string;              // default: "authorization_code"
    clientSecret?: string;           // optional for public clients
}

export interface Tokens {
    accessToken: string;
    refreshToken?: string;
    idToken?: string;
    expiresIn: number;
    expiresAt: number;
    tokenType?: string;
}

export interface TokenResponse {
    access_token: string;
    refresh_token?: string;
    id_token?: string;
    expires_in: number;
    token_type?: string;
    scope?: string;
}

export class OAuth2Manager {
    private config: OAuth2Config;
    private tokens: Tokens | null = null;

    constructor(config: OAuth2Config) {
        this.config = {
            responseType: "code",
            grantType: "authorization_code",
            ...config,
        };
        this.loadTokensFromStorage();
    }

    /**
     * Redirect user to OAuth2 provider login
     */
    redirectToLogin(): void {
        const state = this.generateState();
        const nonce = this.generateNonce();

        // Store state and nonce for validation in callback
        sessionStorage.setItem("oauth2_state", state);
        sessionStorage.setItem("oauth2_nonce", nonce);

        const params = new URLSearchParams({
            client_id: this.config.clientId,
            redirect_uri: this.config.redirectUri,
            response_type: this.config.responseType || "code",
            scope: this.config.scope,
            state,
            nonce,
        });

        window.location.href = `${this.config.authorizationEndpoint}?${params}`;
    }

    /**
     * Handle OAuth2 callback after user logs in at provider
     * Validates state parameter and exchanges code for tokens
     */
    async handleCallback(code: string, state: string): Promise<Tokens> {
        // Validate state parameter (CSRF protection)
        const savedState = sessionStorage.getItem("oauth2_state");
        if (state !== savedState) {
            sessionStorage.removeItem("oauth2_state");
            sessionStorage.removeItem("oauth2_nonce");
            throw new Error("State parameter mismatch - potential CSRF attack");
        }

        sessionStorage.removeItem("oauth2_state");

        const tokens = await this.exchangeCodeForTokens(code);
        this.setTokens(tokens);
        return tokens;
    }

    /**
     * Exchange authorization code for access token
     */
    private async exchangeCodeForTokens(code: string): Promise<Tokens> {
        // Call backend endpoint for secure token exchange
        // The backend will use the client secret securely
        try {
            const response = await fetch("/api/v1/oauth2/token", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                },
                body: JSON.stringify({
                    code,
                    redirectUri: this.config.redirectUri,
                }),
            });

            if (!response.ok) {
                try {
                    const errorData = await response.json();
                    throw new Error(`Token exchange failed: ${errorData.error || errorData.message || response.statusText}`);
                } catch (e) {
                    throw new Error(`Token exchange failed with status ${response.status}: ${response.statusText}`);
                }
            }

            const data = await response.json();
            
            // Parse the token response from the backend
            const tokenResponse = JSON.parse(data.tokenResponse);

            return {
                accessToken: tokenResponse.access_token,
                refreshToken: tokenResponse.refresh_token,
                idToken: tokenResponse.id_token,
                expiresIn: tokenResponse.expires_in,
                expiresAt: Date.now() + tokenResponse.expires_in * 1000,
                tokenType: tokenResponse.token_type || "Bearer",
            };
        } catch (error) {
            console.error("Token exchange error:", error);
            throw error;
        }
    }

    /**
     * Refresh access token using refresh token
     */
    async refreshAccessToken(): Promise<string> {
        if (!this.tokens?.refreshToken) {
            throw new Error("No refresh token available");
        }

        try {
            // Call backend endpoint for secure token refresh
            const response = await fetch("/api/v1/oauth2/refresh", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                },
                body: JSON.stringify({
                    refreshToken: this.tokens.refreshToken,
                }),
            });

            if (!response.ok) {
                try {
                    const errorData = await response.json();
                    throw new Error(`Token refresh failed: ${errorData.error || response.statusText}`);
                } catch (e) {
                    throw new Error(`Token refresh failed with status ${response.status}: ${response.statusText}`);
                }
            }

            const data = await response.json();
            
            // Parse the token response from the backend
            const tokenResponse = JSON.parse(data.tokenResponse);

            const newTokens: Tokens = {
                accessToken: tokenResponse.access_token,
                refreshToken: tokenResponse.refresh_token || this.tokens.refreshToken,
                idToken: tokenResponse.id_token,
                expiresIn: tokenResponse.expires_in,
                expiresAt: Date.now() + tokenResponse.expires_in * 1000,
                tokenType: tokenResponse.token_type || "Bearer",
            };

            this.setTokens(newTokens);
            return newTokens.accessToken;
        } catch (error) {
            console.error("Token refresh error:", error);
            this.clearTokens();
            throw error;
        }
    }

    /**
     * Logout and redirect to provider logout endpoint
     */
    logout(): void {
        this.clearTokens();

        const params = new URLSearchParams({
            client_id: this.config.clientId,
            post_logout_redirect_uri: this.config.postLogoutRedirectUri || this.config.redirectUri,
        });

        // Redirect to provider logout
        window.location.href = `${this.config.logoutEndpoint}?${params}`;
    }

    /**
     * Get current access token (does not refresh)
     */
    getAccessToken(): string | null {
        if (this.isTokenExpired()) {
            return null;
        }
        return this.tokens?.accessToken || null;
    }

    /**
     * Check if current token is expired
     */
    isTokenExpired(): boolean {
        if (!this.tokens?.expiresAt) return true;
        // Add 60 second buffer before actual expiration
        return Date.now() > this.tokens.expiresAt - 60000;
    }

    /**
     * Check if tokens exist
     */
    hasTokens(): boolean {
        // Consider tokens present if we have either an access token or a refresh token.
        // This allows the app to attempt a refresh on page reload when the access token
        // has expired but a refresh token is available.
        return this.tokens !== null && (this.tokens.accessToken !== null || this.tokens.refreshToken !== null);
    }

    /**
     * Set tokens and persist to storage
     */
    setTokens(tokens: Tokens): void {
        this.tokens = tokens;
        this.saveTokensToStorage();
    }

    /**
     * Get all current tokens (for debugging or passing to backend)
     */
    getTokens(): Tokens | null {
        return this.tokens;
    }

    /**
     * Save tokens to session storage
     * In production, use httpOnly cookies instead
     */
    private saveTokensToStorage(): void {
        if (this.tokens) {
            // Use sessionStorage (cleared when browser closes)
            // For production: backend should set httpOnly cookies
            sessionStorage.setItem("oauth2_tokens", JSON.stringify(this.tokens));
        }
    }

    /**
     * Load tokens from session storage
     */
    private loadTokensFromStorage(): void {
        try {
            const stored = sessionStorage.getItem("oauth2_tokens");
            if (stored) {
                this.tokens = JSON.parse(stored);
                // If tokens are expired we keep them in memory only if a refresh token is present
                // so that the app can attempt to refresh them on initialization. Only completely
                // remove stored tokens if there is no refresh token or parsing failed.
                if (this.isTokenExpired() && !this.tokens?.refreshToken) {
                    this.tokens = null;
                    sessionStorage.removeItem("oauth2_tokens");
                }
            }
        } catch (error) {
            console.error("Failed to load tokens from storage:", error);
            sessionStorage.removeItem("oauth2_tokens");
        }
    }

    /**
     * Clear all tokens
     */
    private clearTokens(): void {
        this.tokens = null;
        sessionStorage.removeItem("oauth2_tokens");
        sessionStorage.removeItem("oauth2_state");
        sessionStorage.removeItem("oauth2_nonce");
    }

    /**
     * Generate random state for CSRF protection
     */
    private generateState(): string {
        return Math.random().toString(36).substring(2, 15) +
               Math.random().toString(36).substring(2, 15);
    }

    /**
     * Generate random nonce for OIDC
     */
    private generateNonce(): string {
        return Math.random().toString(36).substring(2, 15) +
               Math.random().toString(36).substring(2, 15);
    }
}

export default OAuth2Manager;

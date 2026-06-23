package io.kestra.webserver.configurations;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * OAuth2 configuration for OIDC-compliant providers (Keycloak, Auth0, etc.)
 */
@Getter
@Setter
@ConfigurationProperties("kestra.server.oauth2")
public class OAuth2Configuration {
    
    /**
     * Enable OAuth2 authentication
     */
    private boolean enabled = false;
    
    /**
     * OAuth2 provider name (e.g., "keycloak", "auth0", "google")
     */
    @Nullable
    private String provider;
    
    /**
     * OAuth2 client ID
     */
    @Nullable
    private String clientId;
    
    /**
     * OAuth2 client secret (optional for public clients)
     */
    @Nullable
    private String clientSecret;
    
    /**
     * Authorization endpoint URL
     * Example: https://keycloak.example.com/auth/realms/master/protocol/openid-connect/auth
     */
    @Nullable
    private String authorizationEndpoint;
    
    /**
     * Token endpoint URL
     * Example: https://keycloak.example.com/auth/realms/master/protocol/openid-connect/token
     */
    @Nullable
    private String tokenEndpoint;
    
    /**
     * User info endpoint URL
     * Example: https://keycloak.example.com/auth/realms/master/protocol/openid-connect/userinfo
     */
    @Nullable
    private String userInfoEndpoint;
    
    /**
     * Logout endpoint URL
     * Example: https://keycloak.example.com/auth/realms/master/protocol/openid-connect/logout
     */
    @Nullable
    private String logoutEndpoint;
    
    /**
     * JWKS endpoint URL for token validation
     * Example: https://keycloak.example.com/auth/realms/master/protocol/openid-connect/certs
     */
    @Nullable
    private String jwksEndpoint;
    
    /**
     * OAuth2 scope
     */
    private String scope = "openid profile email";
    
    /**
     * Token issuer (for validation)
     * Example: https://keycloak.example.com/auth/realms/master
     */
    @Nullable
    private String issuer;
    
    /**
     * Token audience (for validation)
     */
    @Nullable
    private String audience;
    
    /**
     * URLs that should be accessible without authentication
     */
    private List<String> openUrls = List.of();
    
    /**
     * Enable token introspection endpoint
     */
    private boolean enableIntrospection = false;
    
    /**
     * Token introspection endpoint URL
     */
    @Nullable
    private String introspectionEndpoint;
    
    /**
     * Custom claim path for extracting roles from token
     * Use dot notation for nested claims, e.g., "resource_access.kestra.roles"
     * Default providers: Keycloak uses "realm_access.roles", Auth0 uses "roles"
     */
    @Nullable
    private String roleClaimPath;
}

package io.kestra.webserver.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.webserver.configurations.AuthorizationConfiguration;
import io.kestra.webserver.configurations.OAuth2Configuration;
import io.kestra.webserver.models.auth.Permission;
import io.kestra.webserver.models.auth.Role;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for OAuth2 token validation and user info retrieval
 */
@Slf4j
@Singleton
@Requires(property = "kestra.server.oauth2.enabled", value = "true")
@Requires(property = "kestra.server-type", pattern = "(WEBSERVER|STANDALONE)")
public class OAuth2Service {
    
    private final OAuth2Configuration oauth2Configuration;
    private final AuthorizationConfiguration authorizationConfiguration;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    @Inject
    public OAuth2Service(
        OAuth2Configuration oauth2Configuration,
        AuthorizationConfiguration authorizationConfiguration,
        ObjectMapper objectMapper
    ) {
        this.oauth2Configuration = oauth2Configuration;
        this.authorizationConfiguration = authorizationConfiguration;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }
    
    /**
     * Validate access token by calling userinfo endpoint
     * This is simpler than JWT validation and works with opaque tokens
     */
    public Optional<io.kestra.webserver.models.auth.UserInfo> validateToken(String accessToken) {
        if (StringUtils.isBlank(accessToken)) {
            return Optional.empty();
        }
        
        if (StringUtils.isBlank(oauth2Configuration.getUserInfoEndpoint())) {
            log.warn("UserInfo endpoint not configured, cannot validate token");
            return Optional.empty();
        }
        
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(oauth2Configuration.getUserInfoEndpoint()))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                @SuppressWarnings("unchecked")
                Map<String, Object> userInfoMap = objectMapper.readValue(response.body(), Map.class);
                UserInfo rawUserInfo = UserInfo.fromMap(userInfoMap);

                // Extract roles from userinfo claims
                List<Role> roles = extractRolesFromClaims(rawUserInfo.additionalClaims());

                // Fallback: if userinfo doesn't contain roles, try to decode roles from JWT access token
                if (roles.isEmpty()) {
                    Map<String, Object> jwtClaims = decodeJwtClaims(accessToken);
                    if (!jwtClaims.isEmpty()) {
                        roles = extractRolesFromClaims(jwtClaims);
                    }
                }

                // Default to OPERATOR if still no roles found
                if (roles.isEmpty()) {
                    log.warn("No roles found in token claims, defaulting to OPERATOR");
                    roles = List.of(Role.OPERATOR);
                }

                log.debug("Extracted roles for user {}: {}", rawUserInfo.getUsername(), roles);

                // Get permissions for the roles
                Set<Permission> permissions = authorizationConfiguration.getPermissionsForRoles(roles);
                
                return Optional.of(io.kestra.webserver.models.auth.UserInfo.builder()
                    .username(rawUserInfo.getUsername())
                    .email(rawUserInfo.email())
                    .name(rawUserInfo.name())
                    .roles(roles)
                    .permissions(permissions)
                    .build());
            } else {
                log.warn("Token validation failed with status code: {}", response.statusCode());
                return Optional.empty();
            }
        } catch (IOException | InterruptedException e) {
            log.error("Error validating token", e);
            return Optional.empty();
        }
    }
    
    /**
     * Extract roles from token claims
     * Supports multiple claim structures from different OAuth2 providers
     */
    @SuppressWarnings("unchecked")
    private List<Role> extractRolesFromClaims(Map<String, Object> claims) {
        List<Role> roles = new ArrayList<>();
        
        // Try Keycloak realm_access structure
        Object realmAccess = claims.get("realm_access");
        if (realmAccess instanceof Map) {
            Object rolesObj = ((Map<String, Object>) realmAccess).get("roles");
            if (rolesObj instanceof List) {
                roles.addAll(parseRoleList((List<?>) rolesObj));
            }
        }
        
        // Try Auth0/generic "roles" claim
        Object rolesObj = claims.get("roles");
        if (rolesObj instanceof List) {
            roles.addAll(parseRoleList((List<?>) rolesObj));
        }

        // Try Keycloak client roles under resource_access.<clientId>.roles
        Object resourceAccess = claims.get("resource_access");
        if (resourceAccess instanceof Map) {
            Map<String, Object> resourceAccessMap = (Map<String, Object>) resourceAccess;

            String clientId = oauth2Configuration.getClientId();
            if (StringUtils.isNotBlank(clientId)) {
                Object clientAccess = resourceAccessMap.get(clientId);
                if (clientAccess instanceof Map) {
                    Object clientRoles = ((Map<String, Object>) clientAccess).get("roles");
                    if (clientRoles instanceof List) {
                        roles.addAll(parseRoleList((List<?>) clientRoles));
                    }
                }
            }

            // Fallback: collect roles from any client if clientId is missing
            if (roles.isEmpty()) {
                for (Object clientAccessObj : resourceAccessMap.values()) {
                    if (clientAccessObj instanceof Map) {
                        Object clientRoles = ((Map<String, Object>) clientAccessObj).get("roles");
                        if (clientRoles instanceof List) {
                            roles.addAll(parseRoleList((List<?>) clientRoles));
                        }
                    }
                }
            }
        }
        
        // Try "groups" claim (common in Azure AD, Google Workspace)
        Object groupsObj = claims.get("groups");
        if (groupsObj instanceof List) {
            roles.addAll(parseRoleList((List<?>) groupsObj));
        }
        
        // Try custom claim path if configured
        String customRoleClaim = oauth2Configuration.getRoleClaimPath();
        if (StringUtils.isNotBlank(customRoleClaim)) {
            Object customRoles = getNestedClaim(claims, customRoleClaim);
            if (customRoles instanceof List) {
                roles.addAll(parseRoleList((List<?>) customRoles));
            }
        }
        
        return roles.stream().distinct().collect(Collectors.toList());
    }

    /**
     * Decode JWT claims without signature verification (userinfo already validated the token)
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> decodeJwtClaims(String accessToken) {
        try {
            if (StringUtils.isBlank(accessToken) || !accessToken.contains(".")) {
                return Collections.emptyMap();
            }

            String[] parts = accessToken.split("\\.");
            if (parts.length < 2) {
                return Collections.emptyMap();
            }

            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            return objectMapper.readValue(payload, Map.class);
        } catch (Exception e) {
            log.debug("Failed to decode JWT claims", e);
            return Collections.emptyMap();
        }
    }
    
    /**
     * Parse list of role strings into Role enums
     */
    private List<Role> parseRoleList(List<?> roleList) {
        return roleList.stream()
            .filter(obj -> obj instanceof String)
            .map(obj -> Role.fromString((String) obj))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    /**
     * Get nested claim value using dot notation (e.g., "resource_access.kestra.roles")
     */
    @SuppressWarnings("unchecked")
    private Object getNestedClaim(Map<String, Object> claims, String path) {
        String[] parts = path.split("\\.");
        Object current = claims;
        
        for (String part : parts) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<String, Object>) current).get(part);
            if (current == null) {
                return null;
            }
        }
        
        return current;
    }
    
    /**
     * Introspect token using OAuth2 introspection endpoint
     * This is more accurate but requires introspection endpoint configuration
     */
    public Optional<TokenIntrospectionResponse> introspectToken(String accessToken) {
        if (!oauth2Configuration.isEnableIntrospection() || 
            StringUtils.isBlank(oauth2Configuration.getIntrospectionEndpoint())) {
            log.debug("Token introspection not enabled or endpoint not configured");
            return Optional.empty();
        }
        
        try {
            String body = "token=" + accessToken + "&token_type_hint=access_token";
            
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(oauth2Configuration.getIntrospectionEndpoint()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(body));
            
            // Add client credentials if configured
            if (StringUtils.isNotBlank(oauth2Configuration.getClientId()) && 
                StringUtils.isNotBlank(oauth2Configuration.getClientSecret())) {
                String credentials = oauth2Configuration.getClientId() + ":" + oauth2Configuration.getClientSecret();
                String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());
                requestBuilder.header("Authorization", "Basic " + encodedCredentials);
            }
            
            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                TokenIntrospectionResponse introspection = objectMapper.readValue(
                    response.body(), 
                    TokenIntrospectionResponse.class
                );
                return Optional.of(introspection);
            } else {
                log.warn("Token introspection failed with status code: {}", response.statusCode());
                return Optional.empty();
            }
        } catch (IOException | InterruptedException e) {
            log.error("Error introspecting token", e);
            return Optional.empty();
        }
    }
    
    /**
     * Check if OAuth2 is enabled
     */
    public boolean isEnabled() {
        return oauth2Configuration.isEnabled();
    }
    
    /**
     * Get OAuth2 configuration
     */
    public OAuth2Configuration getConfiguration() {
        return oauth2Configuration;
    }
    
    /**
     * User information extracted from userinfo endpoint
     */
    public record UserInfo(
        String sub,
        @Nullable String email,
        @Nullable String name,
        @Nullable String preferredUsername,
        @Nullable String givenName,
        @Nullable String familyName,
        @Nullable Boolean emailVerified,
        Map<String, Object> additionalClaims
    ) {
        public static UserInfo fromMap(Map<String, Object> map) {
            return new UserInfo(
                (String) map.get("sub"),
                (String) map.get("email"),
                (String) map.get("name"),
                (String) map.get("preferred_username"),
                (String) map.get("given_name"),
                (String) map.get("family_name"),
                (Boolean) map.get("email_verified"),
                map
            );
        }
        
        public String getUsername() {
            if (preferredUsername != null) {
                return preferredUsername;
            }
            if (email != null) {
                return email;
            }
            return sub;
        }
    }
    
    /**
     * Token introspection response as per RFC 7662
     */
    public record TokenIntrospectionResponse(
        boolean active,
        @Nullable String scope,
        @Nullable String clientId,
        @Nullable String username,
        @Nullable String tokenType,
        @Nullable Long exp,
        @Nullable Long iat,
        @Nullable Long nbf,
        @Nullable String sub,
        @Nullable String aud,
        @Nullable String iss,
        @Nullable String jti
    ) {}
}

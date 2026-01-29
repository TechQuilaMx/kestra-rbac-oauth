package io.kestra.webserver.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.webserver.configurations.OAuth2Configuration;
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
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * Service for OAuth2 token validation and user info retrieval
 */
@Slf4j
@Singleton
@Requires(property = "kestra.server.oauth2.enabled", value = "true")
@Requires(property = "kestra.server-type", pattern = "(WEBSERVER|STANDALONE)")
public class OAuth2Service {
    
    private final OAuth2Configuration oauth2Configuration;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    @Inject
    public OAuth2Service(OAuth2Configuration oauth2Configuration, ObjectMapper objectMapper) {
        this.oauth2Configuration = oauth2Configuration;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }
    
    /**
     * Validate access token by calling userinfo endpoint
     * This is simpler than JWT validation and works with opaque tokens
     */
    public Optional<UserInfo> validateToken(String accessToken) {
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
                return Optional.of(UserInfo.fromMap(userInfoMap));
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

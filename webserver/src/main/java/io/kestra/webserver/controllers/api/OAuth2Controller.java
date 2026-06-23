package io.kestra.webserver.controllers.api;

import io.kestra.webserver.configurations.OAuth2Configuration;
import io.kestra.webserver.services.OAuth2Service;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * OAuth2 API Controller
 * Handles secure server-side token exchange
 */
@Slf4j
@Controller("/api/v1/oauth2")
@Requires(property = "kestra.server.oauth2.enabled", value = "true")
@Requires(property = "kestra.server-type", pattern = "(WEBSERVER|STANDALONE)")
@Tag(name = "OAuth2", description = "OAuth2 authentication endpoints")
public class OAuth2Controller {
    
    @Inject
    Optional<OAuth2Service> oauth2Service = Optional.empty();
    
    private final HttpClient httpClient;
    
    public OAuth2Controller() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }
    
    /**
     * Exchange authorization code for access token
     * This is called by the frontend after user logs in at OAuth2 provider
     */
    @Post("/token")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(summary = "Exchange authorization code for access token", description = "Server-side token exchange for security")
    public HttpResponse<TokenExchangeResponse> exchangeToken(
        @Body TokenExchangeRequest request
    ) {
        if (!oauth2Service.isPresent()) {
            return HttpResponse.serverError().body(new TokenExchangeResponse(null, "OAuth2 service not configured"));
        }
        
        OAuth2Service service = oauth2Service.get();
        OAuth2Configuration config = service.getConfiguration();
        
        // Validate request
        if (StringUtils.isBlank(request.code)) {
            return HttpResponse.badRequest().body(new TokenExchangeResponse(null, "Authorization code is required"));
        }
        
        if (StringUtils.isBlank(request.redirectUri)) {
            return HttpResponse.badRequest().body(new TokenExchangeResponse(null, "Redirect URI is required"));
        }
        
        log.info("Token exchange request received - Code: {}, Redirect URI: {}", request.code, request.redirectUri);
        log.info("OAuth2 Config - Client ID: {}, Token Endpoint: {}", config.getClientId(), config.getTokenEndpoint());
        
        try {
            // Build token exchange request body with proper URL encoding
            String body = "grant_type=authorization_code" +
                "&code=" + URLEncoder.encode(request.code, StandardCharsets.UTF_8) +
                "&client_id=" + URLEncoder.encode(config.getClientId(), StandardCharsets.UTF_8) +
                "&client_secret=" + URLEncoder.encode(config.getClientSecret(), StandardCharsets.UTF_8) +
                "&redirect_uri=" + URLEncoder.encode(request.redirectUri, StandardCharsets.UTF_8);
            
            log.info("Sending token exchange request to: {}", config.getTokenEndpoint());
            log.debug("Request body (without secret): grant_type=authorization_code&code={}...", request.code);
            
            // Make token exchange request to OAuth2 provider
            HttpRequest tokenRequest = HttpRequest.newBuilder()
                .uri(URI.create(config.getTokenEndpoint()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            
            java.net.http.HttpResponse<String> response = httpClient.send(tokenRequest, java.net.http.HttpResponse.BodyHandlers.ofString());
            
            log.info("Token exchange response status: {} from {}", response.statusCode(), config.getTokenEndpoint());
            log.debug("Token exchange response body: {}", response.body());
            
            if (response.statusCode() == 200) {
                log.info("Successfully exchanged authorization code for access token");
                return HttpResponse.ok(new TokenExchangeResponse(response.body(), null));
            } else {
                log.error("Token exchange failed with status code: {}. Response: {}", 
                    response.statusCode(), response.body());
                String errorMsg = "Token exchange failed";
                if (!response.body().isEmpty()) {
                    errorMsg += ": " + response.body();
                }
                return HttpResponse.status(io.micronaut.http.HttpStatus.UNAUTHORIZED)
                    .body(new TokenExchangeResponse(null, errorMsg));
            }
        } catch (IOException | InterruptedException e) {
            log.error("Error during token exchange", e);
            String errorMsg = "Token exchange error";
            if (e.getMessage() != null) {
                errorMsg += ": " + e.getMessage();
            }
            return HttpResponse.status(io.micronaut.http.HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new TokenExchangeResponse(null, errorMsg));
        }
    }

    /**
     * Refresh access token using refresh token
     * This is called by the frontend when the access token expires
     */
    @Post("/refresh")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(summary = "Refresh access token using refresh token", description = "Server-side token refresh for security")
    public HttpResponse<TokenExchangeResponse> refreshToken(
        @Body TokenRefreshRequest request
    ) {
        if (!oauth2Service.isPresent()) {
            return HttpResponse.serverError().body(new TokenExchangeResponse(null, "OAuth2 service not configured"));
        }
        
        OAuth2Service service = oauth2Service.get();
        OAuth2Configuration config = service.getConfiguration();
        
        // Validate request
        if (StringUtils.isBlank(request.refreshToken)) {
            return HttpResponse.badRequest().body(new TokenExchangeResponse(null, "Refresh token is required"));
        }
        
        log.info("Token refresh request received");
        
        try {
            // Build token refresh request body with proper URL encoding
            String body = "grant_type=refresh_token" +
                "&refresh_token=" + URLEncoder.encode(request.refreshToken, StandardCharsets.UTF_8) +
                "&client_id=" + URLEncoder.encode(config.getClientId(), StandardCharsets.UTF_8) +
                "&client_secret=" + URLEncoder.encode(config.getClientSecret(), StandardCharsets.UTF_8);
            
            log.info("Sending token refresh request to: {}", config.getTokenEndpoint());
            
            // Make token refresh request to OAuth2 provider
            HttpRequest tokenRequest = HttpRequest.newBuilder()
                .uri(URI.create(config.getTokenEndpoint()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            
            java.net.http.HttpResponse<String> response = httpClient.send(tokenRequest, java.net.http.HttpResponse.BodyHandlers.ofString());
            
            log.info("Token refresh response status: {} from {}", response.statusCode(), config.getTokenEndpoint());
            log.debug("Token refresh response body: {}", response.body());
            
            if (response.statusCode() == 200) {
                log.info("Successfully refreshed access token");
                return HttpResponse.ok(new TokenExchangeResponse(response.body(), null));
            } else {
                log.error("Token refresh failed with status code: {}. Response: {}", 
                    response.statusCode(), response.body());
                String errorMsg = "Token refresh failed";
                if (!response.body().isEmpty()) {
                    errorMsg += ": " + response.body();
                }
                return HttpResponse.status(io.micronaut.http.HttpStatus.UNAUTHORIZED)
                    .body(new TokenExchangeResponse(null, errorMsg));
            }
        } catch (IOException | InterruptedException e) {
            log.error("Error during token refresh", e);
            String errorMsg = "Token refresh error";
            if (e.getMessage() != null) {
                errorMsg += ": " + e.getMessage();
            }
            return HttpResponse.status(io.micronaut.http.HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new TokenExchangeResponse(null, errorMsg));
        }
    }
    
    /**
     * Token refresh request
     */
    @Getter
    @Builder
    public static class TokenRefreshRequest {
        String refreshToken;
    }
    
    /**
     * Token exchange request
     */
    @Getter
    @Builder
    public static class TokenExchangeRequest {
        String code;
        String redirectUri;
        @Nullable
        String state;
    }
    
    /**
     * Token exchange response
     */
    @Getter
    @Builder
    public static class TokenExchangeResponse {
        /**
         * Raw JSON response from OAuth2 provider (contains access_token, refresh_token, etc.)
         */
        String tokenResponse;
        
        /**
         * Error message if exchange failed
         */
        @Nullable
        String error;
        
        public TokenExchangeResponse(String tokenResponse, String error) {
            this.tokenResponse = tokenResponse;
            this.error = error;
        }
    }
}

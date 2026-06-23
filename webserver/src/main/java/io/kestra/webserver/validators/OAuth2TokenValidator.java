package io.kestra.webserver.validators;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import io.kestra.webserver.configurations.OAuth2Configuration;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Validator for OAuth2 JWT tokens using JWKS
 * This provides more robust validation than just calling userinfo endpoint
 */
@Slf4j
@Singleton
@Requires(property = "kestra.server.oauth2.enabled", value = "true")
@Requires(property = "kestra.server-type", pattern = "(WEBSERVER|STANDALONE)")
public class OAuth2TokenValidator {
    
    private final OAuth2Configuration oauth2Configuration;
    private ConfigurableJWTProcessor<SecurityContext> jwtProcessor;
    
    @Inject
    public OAuth2TokenValidator(OAuth2Configuration oauth2Configuration) {
        this.oauth2Configuration = oauth2Configuration;
        initializeJWTProcessor();
    }
    
    /**
     * Initialize JWT processor with JWKS endpoint
     */
    private void initializeJWTProcessor() {
        if (StringUtils.isBlank(oauth2Configuration.getJwksEndpoint())) {
            log.warn("JWKS endpoint not configured, JWT validation will not be available");
            return;
        }
        
        try {
            ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
            
            // Set up JWKS source
            JWKSource<SecurityContext> keySource = new RemoteJWKSet<>(
                new URL(oauth2Configuration.getJwksEndpoint())
            );
            
            // Configure JWT processor to use JWKS
            JWSKeySelector<SecurityContext> keySelector = new JWSVerificationKeySelector<>(
                JWSAlgorithm.RS256, // Most OAuth2 providers use RS256
                keySource
            );
            processor.setJWSKeySelector(keySelector);
            
            this.jwtProcessor = processor;
            log.info("JWT processor initialized with JWKS endpoint: {}", oauth2Configuration.getJwksEndpoint());
        } catch (MalformedURLException e) {
            log.error("Invalid JWKS endpoint URL: {}", oauth2Configuration.getJwksEndpoint(), e);
        }
    }
    
    /**
     * Validate JWT token
     * 
     * @param token JWT access token
     * @return Optional containing claims if token is valid, empty otherwise
     */
    public Optional<JWTClaimsSet> validateToken(String token) {
        if (jwtProcessor == null) {
            log.warn("JWT processor not initialized, cannot validate token");
            return Optional.empty();
        }
        
        if (StringUtils.isBlank(token)) {
            return Optional.empty();
        }
        
        try {
            // Process and validate the JWT
            JWTClaimsSet claimsSet = jwtProcessor.process(token, null);
            
            // Additional validations
            if (!validateIssuer(claimsSet)) {
                log.warn("Token issuer validation failed");
                return Optional.empty();
            }
            
            if (!validateAudience(claimsSet)) {
                log.warn("Token audience validation failed");
                return Optional.empty();
            }
            
            if (!validateExpiration(claimsSet)) {
                log.warn("Token has expired");
                return Optional.empty();
            }
            
            return Optional.of(claimsSet);
        } catch (ParseException e) {
            log.error("Failed to parse JWT token", e);
            return Optional.empty();
        } catch (BadJOSEException e) {
            log.error("Invalid JWT token signature or claims", e);
            return Optional.empty();
        } catch (JOSEException e) {
            log.error("JWT processing error", e);
            return Optional.empty();
        }
    }
    
    /**
     * Validate token issuer
     */
    private boolean validateIssuer(JWTClaimsSet claimsSet) {
        if (StringUtils.isBlank(oauth2Configuration.getIssuer())) {
            // If issuer not configured, skip validation
            return true;
        }
        
        try {
            String tokenIssuer = claimsSet.getIssuer();
            return oauth2Configuration.getIssuer().equals(tokenIssuer);
        } catch (Exception e) {
            log.error("Error validating issuer", e);
            return false;
        }
    }
    
    /**
     * Validate token audience
     */
    private boolean validateAudience(JWTClaimsSet claimsSet) {
        if (StringUtils.isBlank(oauth2Configuration.getAudience())) {
            // If audience not configured, skip validation
            return true;
        }
        
        try {
            var audiences = claimsSet.getAudience();
            return audiences != null && audiences.contains(oauth2Configuration.getAudience());
        } catch (Exception e) {
            log.error("Error validating audience", e);
            return false;
        }
    }
    
    /**
     * Validate token expiration
     */
    private boolean validateExpiration(JWTClaimsSet claimsSet) {
        try {
            Date expirationTime = claimsSet.getExpirationTime();
            if (expirationTime == null) {
                // If no expiration time, consider it valid (should not happen in practice)
                return true;
            }
            
            return expirationTime.after(Date.from(Instant.now()));
        } catch (Exception e) {
            log.error("Error validating expiration", e);
            return false;
        }
    }
    
    /**
     * Extract username from JWT claims
     */
    public Optional<String> extractUsername(JWTClaimsSet claimsSet) {
        try {
            // Try preferred_username first (common in Keycloak)
            String username = claimsSet.getStringClaim("preferred_username");
            if (username != null) {
                return Optional.of(username);
            }
            
            // Try email
            username = claimsSet.getStringClaim("email");
            if (username != null) {
                return Optional.of(username);
            }
            
            // Fall back to subject
            return Optional.ofNullable(claimsSet.getSubject());
        } catch (ParseException e) {
            log.error("Error extracting username from claims", e);
            return Optional.empty();
        }
    }
}

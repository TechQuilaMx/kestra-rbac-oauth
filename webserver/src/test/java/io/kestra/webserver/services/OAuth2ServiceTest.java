package io.kestra.webserver.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.webserver.configurations.AuthorizationConfiguration;
import io.kestra.webserver.configurations.OAuth2Configuration;
import io.kestra.webserver.models.auth.Role;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OAuth2ServiceTest {

    @Test
    void shouldExtractRolesFromRealmAccess() throws Exception {
        OAuth2Service service = buildService("kestra-app");

        Map<String, Object> claims = Map.of(
            "realm_access", Map.of(
                "roles", List.of("kestra-admin")
            )
        );

        List<Role> roles = invokeExtractRoles(service, claims);
        assertThat(roles).contains(Role.ADMIN);
    }

    @Test
    void shouldExtractRolesFromResourceAccessClient() throws Exception {
        OAuth2Service service = buildService("kestra-app");

        Map<String, Object> claims = Map.of(
            "resource_access", Map.of(
                "kestra-app", Map.of(
                    "roles", List.of("kestra-admin")
                )
            )
        );

        List<Role> roles = invokeExtractRoles(service, claims);
        assertThat(roles).contains(Role.ADMIN);
    }

    @Test
    void shouldExtractRolesFromRolesClaim() throws Exception {
        OAuth2Service service = buildService("kestra-app");

        Map<String, Object> claims = Map.of(
            "roles", List.of("admin")
        );

        List<Role> roles = invokeExtractRoles(service, claims);
        assertThat(roles).contains(Role.ADMIN);
    }

    @Test
    void shouldExtractRolesFromCustomClaimPath() throws Exception {
        OAuth2Configuration config = new OAuth2Configuration();
        config.setClientId("kestra-app");
        config.setRoleClaimPath("resource_access.kestra-app.roles");

        OAuth2Service service = new OAuth2Service(
            config,
            new AuthorizationConfiguration(),
            new ObjectMapper()
        );

        Map<String, Object> claims = Map.of(
            "resource_access", Map.of(
                "kestra-app", Map.of(
                    "roles", List.of("kestra-admin")
                )
            )
        );

        List<Role> roles = invokeExtractRoles(service, claims);
        assertThat(roles).contains(Role.ADMIN);
    }

    private OAuth2Service buildService(String clientId) {
        OAuth2Configuration config = new OAuth2Configuration();
        config.setClientId(clientId);
        return new OAuth2Service(
            config,
            new AuthorizationConfiguration(),
            new ObjectMapper()
        );
    }

    @SuppressWarnings("unchecked")
    private List<Role> invokeExtractRoles(OAuth2Service service, Map<String, Object> claims) throws Exception {
        Method method = OAuth2Service.class.getDeclaredMethod("extractRolesFromClaims", Map.class);
        method.setAccessible(true);
        return (List<Role>) method.invoke(service, claims);
    }

    @Test
    void shouldHandleEmptyRolesClaims() throws Exception {
        OAuth2Service service = buildService("kestra-app");

        Map<String, Object> claims = Map.of(
            "sub", "user-123",
            "email", "user@example.com"
        );

        List<Role> roles = invokeExtractRoles(service, claims);
        assertThat(roles).isEmpty();
    }

    @Test
    void shouldHandleMultipleRoleExtractSources() throws Exception {
        OAuth2Service service = buildService("kestra-app");

        // Claims with roles from both realm_access and resource_access should deduplicate
        Map<String, Object> claims = Map.of(
            "realm_access", Map.of(
                "roles", List.of("kestra-admin")
            ),
            "resource_access", Map.of(
                "kestra-app", Map.of(
                    "roles", List.of("kestra-admin")
                )
            )
        );

        List<Role> roles = invokeExtractRoles(service, claims);
        // Should contain ADMIN only once (deduplicated)
        assertThat(roles).containsExactly(Role.ADMIN);
    }

    @Test
    void shouldDecodeJwtClaimsWithoutSignatureVerification() throws Exception {
        OAuth2Service service = buildService("kestra-app");

        // A valid JWT structure (header.payload.signature) where payload contains roles
        // This is a test JWT with payload: {"realm_access":{"roles":["kestra-admin"]}}
        String testPayload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"realm_access\":{\"roles\":[\"kestra-admin\"]}}".getBytes());
        String jwtToken = "eyJhbGciOiJIUzI1NiJ9." + testPayload + ".signature";

        Method method = OAuth2Service.class.getDeclaredMethod("decodeJwtClaims", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> decodedClaims = (Map<String, Object>) method.invoke(service, jwtToken);

        assertThat(decodedClaims).isNotEmpty();
        assertThat(decodedClaims).containsKey("realm_access");
    }
}


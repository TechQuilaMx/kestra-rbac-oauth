package io.kestra.webserver.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.webserver.configurations.AuthorizationConfiguration;
import io.kestra.webserver.configurations.OAuth2Configuration;
import io.kestra.webserver.models.auth.Role;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
}

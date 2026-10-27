package net.atos.mev.calendarcalculator.setup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

class SecurityConfigurationTest {

    @Test
    void oidcRolesFromKeycloakAndEntraAreConvertedToAuthorities() {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject("oidc-subject-17")
                .claim("roles", List.of("SCHEDULE_ADMIN"))
                .claim("realm_access", Map.of("roles", List.of("CALENDAR_READER")))
                .claim("resource_access", Map.of("calendar-api", Map.of("roles", List.of("CALENDAR_EDITOR"))))
                .build();

        AbstractAuthenticationToken authentication = new SecurityConfiguration().jwtAuthenticationConverter().convert(jwt);

        assertEquals("oidc-subject-17", authentication.getName());
        assertTrue(authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SCHEDULE_ADMIN")));
        assertTrue(authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CALENDAR_READER")));
        assertTrue(authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CALENDAR_EDITOR")));
    }
}

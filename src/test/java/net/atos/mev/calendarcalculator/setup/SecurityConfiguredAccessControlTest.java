package net.atos.mev.calendarcalculator.setup;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SecurityProbeController.class)
@Import(SecurityConfiguration.class)
@TestPropertySource(properties = {
        "calendar.security.oidc.issuer-uri=https://issuer.example.test",
        "calendar.security.oidc.required-authority=ROLE_TEST_ADMIN",
        "calendar.security.oidc.service-authority=ROLE_TEST_SERVICE",
        "calendar.security.oidc.service-client-id=calendar-planning-service"
})
class SecurityConfiguredAccessControlTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void onlyTheExternallyConfiguredAuthorityCanAccessPrivilegedRoute() throws Exception {
        mvc.perform(post("/api/setup/competitions/test/seasons/2026/versions/1/approve")
                .with(jwt().jwt(token -> token.claim("sub", "human-test"))
                        .authorities(new SimpleGrantedAuthority("ROLE_TEST_ADMIN"))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/setup/competitions/test/seasons/2026/versions/1/approve")
                .with(jwt().jwt(token -> token.claim("sub", "human-test"))
                        .authorities(new SimpleGrantedAuthority("ROLE_OTHER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void serviceTokenNeedsTheConfiguredClientAndServiceAuthority() throws Exception {
        mvc.perform(post("/api/setup/competitions/test/seasons/2026/versions/1/approve")
                .with(jwt().jwt(token -> token.claim("sub", "service-account-calendar-planning-service")
                        .claim("azp", "calendar-planning-service"))
                        .authorities(new SimpleGrantedAuthority("ROLE_TEST_SERVICE"))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/setup/competitions/test/seasons/2026/versions/1/approve")
                .with(jwt().jwt(token -> token.claim("sub", "service-account-untrusted")
                        .claim("azp", "other-client"))
                        .authorities(new SimpleGrantedAuthority("ROLE_TEST_SERVICE"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/setup/competitions/test/seasons/2026/versions/1/approve")
                .with(jwt().jwt(token -> token.claim("sub", "service-account-calendar-planning-service")
                        .claim("azp", "calendar-planning-service"))
                        .authorities(new SimpleGrantedAuthority("ROLE_TEST_ADMIN"))))
                .andExpect(status().isForbidden());
    }
}

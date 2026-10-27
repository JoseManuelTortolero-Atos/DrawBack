package net.atos.mev.calendarcalculator.setup;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(SecurityProbeController.class)
@Import(SecurityConfiguration.class)
@TestPropertySource(properties = {
        "calendar.security.oidc.issuer-uri=",
        "calendar.security.oidc.required-authority="
})
class SecurityAccessControlTest {

    @Autowired
    MockMvc mvc;

    @Test
    void privilegedRouteIsDeniedWhenOidcOrAuthorityIsNotConfigured() throws Exception {
        mvc.perform(post("/api/setup/competitions/test/seasons/2026/versions/1/approve")
                .with(user("test").authorities(new SimpleGrantedAuthority("ROLE_TEST_ADMIN"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/setup/competitions/test/seasons/2026/matches/unplan"))
                .andExpect(status().isForbidden());
    }

    @Test
    void nonPrivilegedRouteRemainsAvailableWithoutOidc() throws Exception {
        mvc.perform(get("/probe")).andExpect(status().isOk());
    }

}

@RestController
class SecurityProbeController {
    @PostMapping("/api/setup/competitions/{cid}/seasons/{season}/versions/{number}/approve")
    String privileged() {
        return "ok";
    }

    @GetMapping("/probe")
    String publicEndpoint() {
        return "ok";
    }
}

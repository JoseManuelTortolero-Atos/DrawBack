package net.atos.mev.calendarcalculator.setup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SecurityProbeController.class)
@Import(SecurityConfiguration.class)
@EnabledIfEnvironmentVariable(named = "CALENDAR_OIDC_IT_ISSUER_URI", matches = ".+")
@TestPropertySource(properties = {
        "calendar.security.oidc.issuer-uri=${CALENDAR_OIDC_IT_ISSUER_URI:}",
        "calendar.security.oidc.required-authority=ROLE_SCHEDULE_TEST",
        "calendar.security.oidc.service-authority=ROLE_SCHEDULE_SERVICE",
        "calendar.security.oidc.service-client-id=${CALENDAR_OIDC_IT_SERVICE_CLIENT_ID:}"
})
class OidcProviderIntegrationTest {

    private static final Pattern ACCESS_TOKEN = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");
    private static final String PROTECTED = "/api/setup/competitions/test/seasons/2026/versions/1/approve";

    @Autowired
    MockMvc mvc;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void validHumanAndClientCredentialsTokensNeedTheirConfiguredAuthorities() throws Exception {
        String humanToken = passwordToken(env("CALENDAR_OIDC_IT_ISSUER_URI"), "calendar-user");
        mvc.perform(post(PROTECTED).header("Authorization", "Bearer " + humanToken))
                .andExpect(status().isOk());

        String serviceToken = clientCredentialsToken(env("CALENDAR_OIDC_IT_ISSUER_URI"));
        mvc.perform(post(PROTECTED).header("Authorization", "Bearer " + serviceToken))
                .andExpect(status().isOk());

        String userWithoutAuthority = passwordToken(env("CALENDAR_OIDC_IT_ISSUER_URI"), "calendar-no-role");
        mvc.perform(post(PROTECTED).header("Authorization", "Bearer " + userWithoutAuthority))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/setup/competitions/test/seasons/2026/matches/reschedule")
                .header("Authorization", "Bearer " + userWithoutAuthority))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/setup/competitions/test/seasons/2026/matches/unplan")
                .header("Authorization", "Bearer " + userWithoutAuthority))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidWrongIssuerAndMissingTokensAreRejected() throws Exception {
        mvc.perform(post(PROTECTED).header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
        String wrongIssuerToken = passwordToken(env("CALENDAR_OIDC_IT_WRONG_ISSUER_URI"), "calendar-user");
        mvc.perform(post(PROTECTED).header("Authorization", "Bearer " + wrongIssuerToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(PROTECTED)).andExpect(status().isUnauthorized());
    }

    private String passwordToken(String issuer, String username) throws Exception {
        return requestToken(issuer, "grant_type=password&client_id=" + encode(env("CALENDAR_OIDC_IT_HUMAN_CLIENT_ID"))
                + "&client_secret=" + encode(env("CALENDAR_OIDC_IT_HUMAN_SECRET"))
                + "&username=" + encode(username) + "&password=" + encode(env("CALENDAR_OIDC_IT_USER_PASSWORD")));
    }

    private String clientCredentialsToken(String issuer) throws Exception {
        return requestToken(issuer, "grant_type=client_credentials&client_id="
                + encode(env("CALENDAR_OIDC_IT_SERVICE_CLIENT_ID")) + "&client_secret="
                + encode(env("CALENDAR_OIDC_IT_SERVICE_SECRET")));
    }

    private String requestToken(String issuer, String form) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(issuer + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), "OIDC token endpoint rejected test credentials");
        Matcher matcher = ACCESS_TOKEN.matcher(response.body());
        assertTrue(matcher.find(), "OIDC token response did not contain an access_token");
        return matcher.group(1);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String env(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing isolated OIDC test setting " + name);
        }
        return value;
    }
}

package net.atos.mev.calendarcalculator.setup;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    private static final String[] PROTECTED_ENDPOINTS = {
            "/api/setup/competitions/*/seasons/*/versions/*/approve",
            "/api/setup/competitions/*/seasons/*/matches/status",
            "/api/setup/competitions/*/seasons/*/matches/unplan",
            "/api/setup/competitions/*/seasons/*/matches/reschedule",
            "/api/setup/competitions/*/seasons/*/rounds/reschedule",
            "/api/setup/competitions/*/seasons/*/fixtures/lock"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            @Value("${calendar.security.oidc.issuer-uri:}") String issuerUri,
            @Value("${calendar.security.oidc.required-authority:}") String requiredAuthority,
            @Value("${calendar.security.oidc.service-authority:}") String serviceAuthority,
            @Value("${calendar.security.oidc.service-client-id:}") String serviceClientId,
            ObjectProvider<JwtDecoder> decoders) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        if (issuerUri == null || issuerUri.isBlank()
                || ((requiredAuthority == null || requiredAuthority.isBlank())
                        && (serviceAuthority == null || serviceAuthority.isBlank()))) {
            http.authorizeHttpRequests(authorize -> authorize
                    .requestMatchers(PROTECTED_ENDPOINTS).denyAll()
                    .anyRequest().permitAll());
        } else {
            http.authorizeHttpRequests(authorize -> authorize
                    .requestMatchers(PROTECTED_ENDPOINTS).access((authentication, context) -> {
                        if (!(authentication.get() instanceof JwtAuthenticationToken jwtAuthentication)) {
                            return new AuthorizationDecision(false);
                        }
                        Jwt jwt = jwtAuthentication.getToken();
                        String clientId = jwt.getClaimAsString("azp");
                        if (clientId == null || clientId.isBlank()) clientId = jwt.getClaimAsString("client_id");
                        if (serviceClientId != null && !serviceClientId.isBlank()
                                && serviceClientId.equals(clientId)) {
                            return new AuthorizationDecision(hasAuthority(jwtAuthentication, serviceAuthority));
                        }
                        return new AuthorizationDecision(hasAuthority(jwtAuthentication, requiredAuthority));
                    })
                    .anyRequest().permitAll())
                    .oauth2ResourceServer(resourceServer -> resourceServer
                            .jwt(jwt -> jwt.decoder(decoders.getIfAvailable(() -> JwtDecoders.fromIssuerLocation(issuerUri)))
                                    .jwtAuthenticationConverter(jwtAuthenticationConverter())));
        }
        return http.build();
    }

    private static boolean hasAuthority(JwtAuthenticationToken authentication, String required) {
        return required != null && !required.isBlank()
                && authentication.getAuthorities().stream().anyMatch(authority -> required.trim().equals(authority.getAuthority()));
    }

    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Set<GrantedAuthority> authorities = new HashSet<>();
            Collection<GrantedAuthority> scopeAuthorities = scopes.convert(jwt);
            if (scopeAuthorities != null) authorities.addAll(scopeAuthorities);
            Set<String> roles = new HashSet<>();
            collectRoles(jwt.getClaims().get("roles"), roles);
            collectRoles(jwt.getClaims().get("realm_access"), roles);
            collectResourceRoles(jwt.getClaims().get("resource_access"), roles);
            roles.stream().map(SecurityConfiguration::roleAuthority).map(SimpleGrantedAuthority::new).forEach(authorities::add);
            return authorities;
        });
        converter.setPrincipalClaimName("sub");
        return converter;
    }

    private static String roleAuthority(String role) {
        return role.startsWith("ROLE_") ? role : "ROLE_" + role;
    }

    private static void collectResourceRoles(Object claim, Set<String> roles) {
        if (claim instanceof Map<?, ?> resources) {
            resources.values().forEach(resource -> collectRoles(resource, roles));
        }
    }

    private static void collectRoles(Object claim, Set<String> roles) {
        if (claim instanceof Collection<?> values) {
            values.stream().filter(String.class::isInstance).map(String.class::cast).forEach(roles::add);
        } else if (claim instanceof Map<?, ?> map) {
            collectRoles(map.get("roles"), roles);
        }
    }
}
